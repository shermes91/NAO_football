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

public class VideoController
{
    public enum STAGE {
        SEARCHBALL{public int y;public int x;}, SEARCHGOAL, ADJUSTTOSHOOT
    }

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
    private STAGE stage = STAGE.SEARCHBALL;

    /**
     * Initialize method, automatically called by @{link FXMLLoader}
     */
    public void initialize()
    {
        this.capture = new VideoCapture();
        this.cameraActive = false;
        try{
            initNAO();
        }
        catch(Exception e) {
            System.out.println(e.getStackTrace());
        }

    }

    /**
     * initialise to NAO Robot and get its camera interface
     * @throws Exception
     */
    protected void initNAO() throws Exception {

        int topCamera = 0;
        int bottomCamera = 1;
        int resolution = 1; // 320x240
        int colorspace = 13; // BGR
        int frameRate = 30; // FPS
        com.aldebaran.qi.Application application;

        Session session = new Session();
        String[] strings = {""};
        application = new com.aldebaran.qi.Application(strings);
        try {

            String robotIp = "192.168.1.10";

            session.connect("tcp://" + robotIp + ":9559").sync(500, TimeUnit.MILLISECONDS);

            video = new ALVideoDevice(session);
            moveNao = new MoveNao(session);
            moduleName = video.subscribeCamera(NAO_CAMERA_NAME, topCamera, resolution, colorspace, frameRate);
            System.out.println(moduleName);
            getNaoFrames(video, moduleName);

        } catch(Exception e) {

        }
    }

    /**
     * Run thread to get frames from nao and convert it to Mat
     * @param alVideoDevice
     * @param subscribeCamera
     * @throws InterruptedException
     * @throws CallError
     */
    private void getNaoFrames(ALVideoDevice alVideoDevice, String subscribeCamera) throws InterruptedException, CallError {


        Runnable frameGrabber = new Runnable() {
            Mat pinkPixels = new Mat();
            Mat yellowPixels = new Mat();
            List<Object> imageRemote = null;
            ByteBuffer b;

            //@Override
            public void run() {

                try {
                    imageRemote = (List<Object>) alVideoDevice.getImageRemote(subscribeCamera);
                } catch (CallError callError) {
                    callError.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                b = (ByteBuffer) imageRemote.get(6);
                Mat image = new Mat((int) imageRemote.get(1), (int) imageRemote.get(0), CvType.CV_8UC3);
                image.put(0, 0, b.array());

                switch(stage) {
                    case SEARCHBALL:
                        image = detectCircle(image, pinkPixels);
                        break;
                    case SEARCHGOAL:
                        image = detectGoal(image, yellowPixels);
                        break;
                    case ADJUSTTOSHOOT:
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

    private void preProcessForBallDetection(Mat pinkPixels, Mat image, Scalar lowerP, Scalar upperP) {
        Imgproc.GaussianBlur(image, pinkPixels, new Size(9, 9), 0, 0);

        Imgproc.cvtColor(pinkPixels, pinkPixels, Imgproc.COLOR_BGR2HSV);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(1, 4));

        Imgproc.erode(pinkPixels, pinkPixels, kernel);
        Imgproc.erode(pinkPixels, pinkPixels, kernel);

        Core.inRange(pinkPixels, lowerP, upperP, pinkPixels);
    }

    private Mat detectCircle(Mat src, Mat pinkPixels){

        preProcessForBallDetection(pinkPixels, src, new Scalar(150, 120, 30), new Scalar(180, 255, 255));

        Mat circles = new Mat();

        int minRadius = 2;
        int maxRadius = 50;
        /*Imgproc.HoughCircles(pinkPixels, circles, Imgproc.CV_HOUGH_GRADIENT, 1, minRadius, 120, 10, minRadius, maxRadius);

        if (circles.cols() != 0) {
            for (int x = 0; x < 1;x++) {

                double vCircle[]=circles.get(0,x);

                Point center=new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
                int radius = (int)Math.round(vCircle[2]);
                // draw the circle center
                circle(src, center, 3,new Scalar(0,255,0), -1, 8, 0 );
                // draw the circle outline
                circle( src, center, radius, new Scalar(0,0,255), 3, 8, 0 );
            }
        }*/

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(pinkPixels, contours, new Mat(), Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);

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
        if (maxArea > 20 && contoursMaxId != -1) {
            MatOfPoint ball = contours.get(contoursMaxId);
            Point firstPoint = ball.toArray()[contoursMaxId];
            double leftPointX = firstPoint.x;
            double rightPointX = firstPoint.x;
            double bottomY = firstPoint.y;
            double upperY = firstPoint.y;
            for ( Point p : ball.toArray()) {
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
            drawContours(src, contours, contoursMaxId, new Scalar(0,0,255));
            Point middle = new Point((rightPointX + leftPointX) / 2, (bottomY + upperY) / 2);

            if(moveNao.followTarget(middle, true)){
                moveNao.moveForward();
                stage = STAGE.SEARCHGOAL;
            }
            circle(src, middle, 3,new Scalar(0,255,0), -1, 8, 0 );
        }

        return src;
    }

    private Mat detectGoal(Mat src, Mat yellowPixels){

        Imgproc.GaussianBlur(src, yellowPixels, new Size(9, 9), 0, 0);
        Imgproc.cvtColor(yellowPixels, yellowPixels, Imgproc.COLOR_BGR2HSV);

        Core.inRange(yellowPixels, new Scalar(20, 100, 100), new Scalar(30,255,255), yellowPixels);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));

        Imgproc.dilate(yellowPixels, yellowPixels, kernel);
        Imgproc.dilate(yellowPixels, yellowPixels, kernel);
        Imgproc.dilate(yellowPixels, yellowPixels, kernel);
        Imgproc.erode(yellowPixels, yellowPixels, kernel);
        Imgproc.erode(yellowPixels, yellowPixels, kernel);
        Imgproc.erode(yellowPixels, yellowPixels, kernel);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(yellowPixels, contours, new Mat(), Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);

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
        if (maxArea > 100 && contoursMaxId != -1) {
            MatOfPoint goal = contours.get(contoursMaxId);
            Point firstPoint = goal.toArray()[contoursMaxId];
            double leftPointX = firstPoint.x;
            double rightPointX = firstPoint.x;
            double bottomY = firstPoint.y;
            for ( Point p : goal.toArray()) {
                if (p.x > rightPointX) {
                    rightPointX = p.x;
                }
                if (p.x < leftPointX) {
                    leftPointX = p.x;
                }
                if (p.y > bottomY) {
                    bottomY = p.y;
                }
            }
            drawContours(src, contours, contoursMaxId, new Scalar(0,0,255));
            Point middle = new Point((rightPointX + leftPointX) / 2, bottomY);
            circle(src, middle, 3,new Scalar(0,255,0), -1, 8, 0 );
            if (moveNao.followTarget(middle,true)) {
                
            }
        }

        return src;
    }

    @FXML
    public void shutdown() throws InterruptedException, CallError {
        moveNao.shutdown();
        video.unsubscribe(moduleName);

    }


    /**
     * Update the {@link ImageView} in the JavaFX main thread
     *
     * @param view
     *            the {@link ImageView} to update
     * @param image
     *            the {@link Image} to show
     */
    private void updateImageView(ImageView view, Image image)
    {
        Utils.onFXThread(view.imageProperty(), image);
    }
}
