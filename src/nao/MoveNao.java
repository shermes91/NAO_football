package nao;

import com.aldebaran.qi.CallError;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.helper.proxies.ALMotion;
import com.aldebaran.qi.helper.proxies.ALRobotPosture;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;

public class MoveNao {

    ALMotion alMotion;
    ALRobotPosture alRobotPosture;
    float x;
    float y;
    boolean targetIsHorizontalMiddle;
    boolean targetIsVerticalMiddle;
    float y_gegenkath;
    float x_ankath;
    boolean neverMoved = true;
    int guesscount = 0;

    public void shutdown() {
        System.out.println("Moving to Exitposition");
        ArrayList<String> names = new ArrayList<>();
        names.add("HeadYaw");
        names.add("HeadPitch");
        ArrayList<Float> angles = new ArrayList<>();
        angles.add((float) 0);
        angles.add((float) 0);
        float speed = (float) 0.5;
        try {
            alMotion.setAngles(names, angles, speed);
            alMotion.closeHand("LHand");
            alMotion.closeHand("RHand");
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            alMotion.setStiffnesses("Head", 0);
            alMotion.stopMove();
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public enum Direction {
        LEFT, RIGHT, UP, DOWN
    }

    ;

    public MoveNao(Session session) throws Exception {
        System.out.println("MOVE HEAD");
        alMotion = new ALMotion(session);
        alRobotPosture = new ALRobotPosture(session);
        alMotion.clearStats();
        alMotion.moveInit();
        alMotion.setStiffnesses("Head", 1.0);
        ArrayList<String> names = new ArrayList<>();
        names.add("HeadYaw");
        names.add("HeadPitch");
        ArrayList<Float> angles = new ArrayList<>();
        angles.add((float) 0);
        angles.add((float) 0);
        float speed = (float) 0.5;
        alMotion.setAngles(names, angles, speed);
        Thread.sleep(2000);
        standUp();
        //moveForward();
        //alMotion.setStiffnesses("Head", 0);

    }

    public boolean followTarget(Point p, boolean foundObject) {
        this.x = (float) p.x;
        this.y = (float) p.y;
        //Mitte 160 : 120
        float anglex = Math.abs(x) - 160;
        float angley = (float) ((float) ((120 - y) / 120 * 23.5) * -1 * Math.PI / 360);
        if (x < 140) turnHeadRight();
        else if (x > 180) turnHeadLeft();
        else targetIsHorizontalMiddle = true;
        if (y < 115 | y > 125) turnHeadUpDown(angley);
        else targetIsVerticalMiddle = true;
        if (targetIsHorizontalMiddle && targetIsVerticalMiddle) {
            guessDistance();
            guesscount++;
            if (guesscount == 15) {
                return true;
            }
        }
        return false;
    }

    private void guessDistance() {

        List<Float> body = new ArrayList<>();
        List<String> names = new ArrayList<>();
        names.add("HeadYaw");
        names.add("HeadPitch");
        try {
            body = alMotion.getAngles(names, true);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        float distance = 0;
        System.out.println("Yaw: " + body.get(0));
        System.out.println("Pitch: " + body.get(1));
        distance = calculateDistance(body.get(1));
        System.out.println("Distance: " + distance);
        y_gegenkath = (float) (Math.sin(body.get(0)) * distance);
        x_ankath = (float) (Math.cos(body.get(0)) * distance);
        System.out.println("An: " + x_ankath);
        System.out.println("Gegen: " + y_gegenkath);

    }

    private float calculateDistance(Float pitchAngle) {
        return (float) (Math.tan(Math.PI / 2 - pitchAngle - 0.13) * 0.45);
    }

    private void turnHeadLeft() {
        System.out.println("Turning Left");
        try {
            alMotion.changeAngles("HeadYaw", -0.05, (float) 0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void turnHeadUpDown(float angle) {
        System.out.println("HeadUp");
        try {
            alMotion.changeAngles("HeadPitch", angle, (float) 0.05);
            Thread.sleep(200);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void turnHeadRight() {
        System.out.println("Turning Right");
        try {
            alMotion.changeAngles("HeadYaw", 0.05, (float) 0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void standUp() {
        System.out.println("Standing up");
        try {
            alRobotPosture.goToPosture("StandInit", (float) 0.7);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void moveForward() {
        try {
            if (neverMoved)
                alMotion.moveTo(x_ankath, y_gegenkath, (float) 0);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
