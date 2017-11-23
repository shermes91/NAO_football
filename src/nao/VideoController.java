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
    private final String NAO_CAMERA_NAME = "Nao Image";

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

        int topCamera = 0;
        int resolution = 2; // 640 x 480
        int colorspace = 13; // BGR
        int frameRate = 30; // FPS
        com.aldebaran.qi.Application application;
        ALVideoDevice video;
        String moduleName;

        Session session = new Session();
        String[] strings = {""};
        application = new com.aldebaran.qi.Application(strings);
        try {

            String robotIp = "192.168.1.12";

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

            @Override
            public void run() {
                Mat blue = new Mat();
                List<Object> imageRemote = null;
                try {
                    imageRemote = (List<Object>) alVideoDevice.getImageRemote(subscribeCamera);
                } catch (CallError callError) {
                    callError.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ByteBuffer b = (ByteBuffer) imageRemote.get(6);
                Mat image = new Mat((int) imageRemote.get(1), (int) imageRemote.get(0), CvType.CV_8UC3);
                image.put(0, 0, b.array());

                preProcessForBallDetection(blue, image, new Scalar(160, 100, 100), new Scalar(179, 255, 255));
                image = detectCircle(image, blue);

                // convert and show the frame
                Image imageToShow = Utils.mat2Image(image);
                updateImageView(currentFrame, imageToShow);

            }
        };
        this.timer = Executors.newSingleThreadScheduledExecutor();
        this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
    }

    /**
     * start webcam on notebook for testing
     */
    @FXML
	private void startWebcam()
	{

		// set a fixed width for the frame
		this.currentFrame.setFitWidth(600);
		// preserve image ratio
		this.currentFrame.setPreserveRatio(true);

		if (!this.cameraActive)
		{
			// start the video capture
			this.capture.open(0);

			// is the video stream available?
			if (this.capture.isOpened())
			{
				this.cameraActive = true;

				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = new Runnable() {

					@Override
					public void run()
					{
						// effectively grab and process a single frame
						Mat frame = grabFrame();
						// convert and show the frame
						Image imageToShow = Utils.mat2Image(frame);
						updateImageView(currentFrame, imageToShow);
					}
				};

				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
			}
			else
			{
				// log the error
				System.err.println("Impossible to open the camera connection...");
			}
		}
		else
		{
			// the camera is not active at this point
			this.cameraActive = false;

			// stop the timer
			this.stopAcquisition();
		}
	}

    /**
     * Get frame from notebook camera for testing
     * @return the {@link Image} to show
     */

	private Mat grabFrame()
	{
		Mat frame = new Mat();
		Mat blue = new Mat();
		// check if the capture is open
		if (this.capture.isOpened())
		{
			try
			{
				// read the current frame
				this.capture.read(frame);

				// if the frame is not empty, process it
				if (!frame.empty())
				{
                    preProcessForBallDetection(blue, frame, new Scalar(90,150,0), new Scalar(150,255,255));

					frame = detectCircle(frame, blue);
				}

			}
			catch (Exception e)
			{
				// log the error
				System.err.println("Exception during the frame elaboration: " + e);
			}
		}
		return frame;
	}

    private void preProcessForBallDetection(Mat blue, Mat image, Scalar lowerb, Scalar upperb) {
        Imgproc.GaussianBlur(image, blue, new Size(9, 9), 0, 0);
        Imgproc.cvtColor(blue, blue, Imgproc.COLOR_BGR2HSV);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));

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

        int minRadius = 50;
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
