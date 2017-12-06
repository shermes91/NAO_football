package nao;

import java.nio.ByteBuffer;
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
    @FXML
    private ImageView currentFrame;

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

    /**
     * Initialize method, automatically called by @{link FXMLLoader}
     */
    public void initialize()
    {
        this.capture = new VideoCapture();
        this.cameraActive = false;
        try{
            initNAO();
            //startWebcam();
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

        int topCamera = 1;
        int resolution = 1; // 640 x 480
        int colorspace = 13; // BGR
        int frameRate = 30; // FPS
        com.aldebaran.qi.Application application;

        Session session = new Session();
        String[] strings = {""};
        application = new com.aldebaran.qi.Application(strings);
        try {

            String robotIp = "192.168.1.4";

            session.connect("tcp://" + robotIp + ":9559").sync(500, TimeUnit.MILLISECONDS);

            video = new ALVideoDevice(session);
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
            Mat blue = new Mat();
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
                preProcessForBallDetection(blue, image, new Scalar(125, 100, 30), new Scalar(255, 255, 255));
                image = detectCircle(image, blue);

                // convert and show the frame
                Image imageToShow = Utils.mat2Image(image);
                updateImageView(currentFrame, imageToShow);

            }
        };
        this.timer = Executors.newSingleThreadScheduledExecutor();
        this.timer.scheduleAtFixedRate(frameGrabber, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void preProcessForBallDetection(Mat blue, Mat image, Scalar lowerb, Scalar upperb) {
        Imgproc.GaussianBlur(image, blue, new Size(9, 9), 0, 0);
        Imgproc.cvtColor(blue, blue, Imgproc.COLOR_BGR2HSV);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(8, 8));

        Imgproc.dilate(blue, blue, kernel);
        Imgproc.dilate(blue, blue, kernel);
        Imgproc.dilate(blue, blue, kernel);
        Imgproc.erode(blue, blue, kernel);
        Imgproc.erode(blue, blue, kernel);
        Imgproc.erode(blue, blue, kernel);

        Core.inRange(blue, lowerb, upperb, blue);
    }

    private Mat detectCircle(Mat src, Mat blue){

        Mat circles = new Mat();

        int minRadius = 5;
        int maxRadius = 150;
        Imgproc.HoughCircles(blue, circles, Imgproc.CV_HOUGH_GRADIENT, 1, minRadius, 120, 10, minRadius, maxRadius);

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
        }
        return src;
    }

    /**
     * Stop the acquisition from the camera and release all the resources
     */
    private void stopAcquisition()
    {
        if (this.timer != null && !this.timer.isShutdown())
        {
            try
            {
                // stop the timer
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                // log any exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
        }

        if (this.capture.isOpened())
        {
            // release the camera
            this.capture.release();
        }
    }

    @FXML
    public void shutdown() throws InterruptedException, CallError {
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
