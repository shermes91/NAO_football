package nao;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aldebaran.qi.CallError;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.helper.proxies.ALVideoDevice;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import static org.opencv.imgproc.Imgproc.*;

/**
 * This class controlls the process for scoring a gaol and does the image processing operations
 */
public class VideoController {

    @FXML
    private ImageView currentFrame;
    private MoveNao moveNao;

    // a timer for acquiring the video stream
    private ScheduledExecutorService timer;
    // the OpenCV object that realizes the video capture
    private VideoCapture capture;
    // a flag to change the button behavior
    private boolean cameraActive;
    // the logo to be loaded
    ALVideoDevice video;
    private String NAO_CAMERA_NAME = "Nao Image";
    private String moduleName;
    private static final String NAO_IP = "192.168.1.6";

    /**
     * Initialize method, automatically called by @{link FXMLLoader}
     */
    public void initialize() {
        this.capture = new VideoCapture();
        this.cameraActive = false;
        try {
            initNAO();
        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        }

    }

    /**
     * initialise the Nao for camera access and own defined class MoveNao for Nao movement
     *
     * @throws Exception
     */
    protected void initNAO() throws Exception {

        com.aldebaran.qi.Application application;

        Session session = new Session();
        String[] strings = {""};
        application = new com.aldebaran.qi.Application(strings);
        try {
            session.connect("tcp://" + NAO_IP + ":9559").sync(500, TimeUnit.MILLISECONDS);
            video = new ALVideoDevice(session);
            moveNao = new MoveNao(session);
            moduleName = subscribeCamera(moveNao.stage);
            System.out.println(moduleName);
            getNaoFrames(video, moduleName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * subscribe lower or upper camera, depending in which stage the Nao is in
     * @param stage Stage
     * @return modulename, to identify the subscribes camera
     * @throws CallError
     * @throws InterruptedException
     */
    private String subscribeCamera(MoveNao.STAGE stage) throws CallError, InterruptedException {
        int topCamera = 0;
        int bottomCamera = 1;
        int resolution = 1; // 320x240
        int colorspace = 13; // BGR
        int frameRate = 30; // FPS
        if (stage == MoveNao.STAGE.ADJUSTTOSHOOT)
            return video.subscribeCamera(NAO_CAMERA_NAME, bottomCamera, resolution, colorspace, frameRate);
        else
            return video.subscribeCamera(NAO_CAMERA_NAME, topCamera, resolution, colorspace, frameRate);
    }

    /**
     * Run thread to get frames from nao and convert it to Mat
     *
     * @param alVideoDevice
     * @param moduleName String, which is return from subscribeCamera function
     * @throws InterruptedException
     * @throws CallError
     */
    private void getNaoFrames(ALVideoDevice alVideoDevice, String moduleName) throws InterruptedException, CallError {


        Runnable frameGrabber = new Runnable() {
            Mat pinkPixels = new Mat();
            Mat yellowPixels = new Mat();
            List<Object> imageRemote = null;
            ByteBuffer b;

            //@Override
            public void run() {

                try {
                    imageRemote = (List<Object>) alVideoDevice.getImageRemote(moduleName);
                } catch (CallError callError) {
                    callError.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                b = (ByteBuffer) imageRemote.get(6);
                Mat image = new Mat((int) imageRemote.get(1), (int) imageRemote.get(0), CvType.CV_8UC3);
                image.put(0, 0, b.array());

                switch (moveNao.stage) {
                    case SEARCHBALL:
                        image = detectCircle(image, pinkPixels);
                        break;
                    case SEARCHGOAL:
                        try {
                            image = detectGoal(image, yellowPixels);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (CallError callError) {
                            callError.printStackTrace();
                        }
                        break;
                    case ADJUSTTOSHOOT:
                        image = detectCircle(image, pinkPixels);
                        break;
                }

                // convert and show the frame
                Image imageToShow = Utils.mat2Image(image);
                updateImageView(currentFrame, imageToShow);

            }
        };
        this.timer = Executors.newSingleThreadScheduledExecutor();
        this.timer.scheduleAtFixedRate(frameGrabber, 0, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Preprocess the image for ball recognition
     * @param pinkPixels empty image for the solution
     * @param image original image
     * @param lowerP lower pink range
     * @param upperP upper pink range
     */
    private void preProcessForBallDetection(Mat pinkPixels, Mat image, Scalar lowerP, Scalar upperP) {
        Imgproc.GaussianBlur(image, pinkPixels, new Size(9, 9), 0, 0);

        Imgproc.cvtColor(pinkPixels, pinkPixels, Imgproc.COLOR_BGR2HSV);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(2, 3));

        Core.inRange(pinkPixels, lowerP, upperP, pinkPixels);

        Imgproc.dilate(pinkPixels, pinkPixels, kernel);
        Imgproc.erode(pinkPixels, pinkPixels, kernel);
        Imgproc.dilate(pinkPixels, pinkPixels, kernel);
    }

    /**
     * Function for object recognition of the ball and calculate the center point of it.
     * @param src original image
     * @param pinkPixels empty image for solution
     * @return original image with green center point
     */
    private Mat detectCircle(Mat src, Mat pinkPixels) {

        preProcessForBallDetection(pinkPixels, src, new Scalar(150, 120, 30), new Scalar(180, 255, 255));

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(pinkPixels, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxArea = 0;
        int i = -1;
        int contoursMaxId = -1;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            i++;
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea) {
                maxArea = area;
                contoursMaxId = i;
            }
        }
        if (maxArea > 15 && contoursMaxId != -1) {
            MatOfPoint ball = contours.get(contoursMaxId);
            Point firstPoint = ball.toArray()[0];
            double leftPointX = firstPoint.x;
            double rightPointX = firstPoint.x;
            double bottomY = firstPoint.y;
            double upperY = firstPoint.y;
            for (Point p : ball.toArray()) {
                if (p.x > rightPointX) {
                    rightPointX = p.x;
                }
                if (p.x < leftPointX) {
                    leftPointX = p.x;
                }
                if (p.y > bottomY) {
                    bottomY = p.y;
                }
                if (p.y < upperY) {
                    upperY = p.y;
                }
            }
            drawContours(src, contours, contoursMaxId, new Scalar(0, 0, 255));
            Point middle = new Point((rightPointX + leftPointX) / 2, (bottomY + upperY) / 2);

            if (moveNao.followTarget(middle, true)) {
                if (moveNao.stage == MoveNao.STAGE.SEARCHBALL) {
                    moveNao.moveForward();
                    moveNao.stage = MoveNao.STAGE.SEARCHGOAL;
                    try {
                        moveNao.resetHeadPosition();
                    } catch (CallError callError) {
                        callError.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    moveNao.guesscount = 0;
                } else {
                    moveNao.moveToAdjustment();
                }
            }
            circle(src, middle, 3, new Scalar(0, 255, 0), -1, 8, 0);
        }

        return src;
    }

    /**
     * Function for object recognition of the goal and calculate the center goal line point of it.
     * @param src original image
     * @param yellowPixels empty image for solution
     * @return original image with green cente point
     * @throws InterruptedException
     * @throws CallError
     */
    private Mat detectGoal(Mat src, Mat yellowPixels) throws InterruptedException, CallError {

        Imgproc.GaussianBlur(src, yellowPixels, new Size(5, 5), 0, 0);
        Imgproc.cvtColor(src, yellowPixels, Imgproc.COLOR_BGR2HSV);

        Core.inRange(yellowPixels, new Scalar(20, 100, 100), new Scalar(30, 255, 255), yellowPixels);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));

        Imgproc.dilate(yellowPixels, yellowPixels, kernel);
        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 20));

        Imgproc.erode(yellowPixels, yellowPixels, kernel);
        Imgproc.dilate(yellowPixels, yellowPixels, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint> posts = new ArrayList<>();
        Mat temp = yellowPixels.clone();
        Imgproc.findContours(temp, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        ArrayList<Point> lowestPoints = new ArrayList<>();

        for (int i = 0; i < contours.size(); i++) {
            System.out.println(contourArea(contours.get(i)));
            if (contourArea(contours.get(i)) > 400) {
                posts.add(contours.get(i));
            }
        }
        if (posts.size() == 2) {
            for (int i = 0; i < posts.size(); i++) {

                Point lowestPointForContour = posts.get(i).toArray()[0];
                for (Point p : posts.get(i).toArray()) {
                    if (p.y > lowestPointForContour.y) {
                        lowestPointForContour = p;
                    }
                }
                lowestPoints.add(lowestPointForContour);
            }
            Point leftPointX = lowestPoints.get(0);
            Point rightPointX = lowestPoints.get(0);
            System.out.println(leftPointX);
            System.out.println(rightPointX);

            for (Point p : lowestPoints) {
                if (p.x < leftPointX.x) {
                    leftPointX = p;
                }
                if (p.x > rightPointX.x) {
                    rightPointX = p;
                }
            }

            Point middle = new Point((rightPointX.x + leftPointX.x) / 2, (rightPointX.y + leftPointX.y) / 2 - 15);
            circle(src, middle, 3, new Scalar(0, 255, 0), -1, 8, 0);
            if (moveNao.followTarget(middle, true)) {
                video.unsubscribe(moduleName);
                moveNao.stage = MoveNao.STAGE.ADJUSTTOSHOOT;
                moveNao.guesscount = 0;
                moduleName = subscribeCamera(moveNao.stage);
            }
        } else if (posts.size() == 1) {
            if (posts.get(0).toArray()[0].x < 160) {
                moveNao.turnHeadLeft();
            } else {
                moveNao.turnHeadRight();
            }
        }


        return src;
    }

    /**
     * Is called by closing the window. Bring Nao in initial position and unsubscribe the camera
     * @throws InterruptedException
     * @throws CallError
     */
    @FXML
    public void shutdown() throws InterruptedException, CallError {
        moveNao.shutdown();
        video.unsubscribe(moduleName);

    }


    /**
     * Update the {@link ImageView} in the JavaFX main thread
     * @param view  the {@link ImageView} to update
     * @param image the {@link Image} to show
     */
    private void updateImageView(ImageView view, Image image) {
        Utils.onFXThread(view.imageProperty(), image);
    }
}
