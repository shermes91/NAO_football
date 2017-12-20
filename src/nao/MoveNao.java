package nao;

import com.aldebaran.qi.CallError;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.helper.proxies.ALMotion;
import com.aldebaran.qi.helper.proxies.ALRobotPosture;
import org.opencv.core.Point;

import java.util.ArrayList;

public class MoveNao {

    ALMotion alMotion;
    ALRobotPosture alRobotPosture;
    float x;
    float y;

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
        //standUp();
        //moveForward();
        //alMotion.setStiffnesses("Head", 0);

    }

    public void followBall(Point p, boolean foundObject) {
        this.x = (float) p.x;
        this.y = (float) p.y;
        //Mitte 160 : 120
        if (x < 140) turnHeadRight();
        else if (x > 180) turnHeadLeft();
        if (y < 50) turnHeadDown();
        else if (x > 70) turnHeadUp();
    }

    private void turnHeadLeft() {
        System.out.println("Turning Left");
        try {
            alMotion.changeAngles("HeadYaw", -0.1, (float) 0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void turnHeadUp() {
        System.out.println("HeadUp");
        try {
            alMotion.changeAngles("HeadPitch", 0.1, (float) 0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void turnHeadDown() {
        System.out.println("HeadDown");
        try {
            alMotion.changeAngles("HeadPitch", -0.1, (float) 0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void turnHeadRight() {
        System.out.println("Turning Right");
        try {
            alMotion.changeAngles("HeadYaw", 0.1, (float) 0.05);
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
            alMotion.moveTo((float) 0.5, (float) 0, (float) 0);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
