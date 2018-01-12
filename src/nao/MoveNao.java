package nao;

import com.aldebaran.qi.CallError;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.helper.proxies.ALMotion;
import com.aldebaran.qi.helper.proxies.ALRobotPosture;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;

public class MoveNao {

    public void adjustNaoToShoot() {
        System.out.println("AdjustingNaoToShoot");
    }

    public enum STAGE {
        SEARCHBALL, SEARCHGOAL, ADJUSTTOSHOOT;
        private float x = 0, y = 0;

        STAGE() {
            x = 0;
            y = 0;
        }

        public float getx() {
            return this.x;
        }

        public float gety() {
            return this.x;
        }

        public void setx(float x) {
            this.x = x;
        }

        public void sety(float y) {
            this.y = y;
        }
    }

    ALMotion alMotion;
    ALRobotPosture alRobotPosture;
    public STAGE stage = STAGE.SEARCHBALL;
    boolean targetIsHorizontalMiddle;
    boolean targetIsVerticalMiddle;

    public int guesscount = 0;

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
        stage.x = (float) p.x;
        stage.y = (float) p.y;
        //Mitte 160 : 120
        float anglex = Math.abs(stage.x) - 160;
        float angley = (float) ((float) ((120 - stage.y) / 120 * 23.5) * -1 * Math.PI / 360);
        if (stage.x < 140) turnHeadRight();
        else if (stage.x > 180) turnHeadLeft();
        else targetIsHorizontalMiddle = true;
        if (stage.y < 115 | stage.y > 125) turnHeadUpDown(angley);
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
        Float bodyPitch = body.get(1);
        System.out.println("Pitch: " + bodyPitch);
        if (stage == STAGE.ADJUSTTOSHOOT)
            distance = calculateDistanceLowerCamera(bodyPitch);
        else
            distance = calculateDistanceUpperCamera(bodyPitch);
        System.out.println("Distance: " + distance);
        stage.y = (float) (Math.sin(body.get(0)) * distance);
        stage.x = (float) (Math.cos(body.get(0)) * distance);
        System.out.println("An: " + stage.x);
        System.out.println("Gegen: " + stage.y);

    }

    private float calculateDistanceUpperCamera(float pitchAngle) {
        System.out.println("TOP");
        System.out.println("Pitch: " + pitchAngle);
        double v = Math.tan(Math.PI / 2 - (pitchAngle + (float) 0.12)) * 0.49;
        System.out.println("Ergebnis: " + v);
        return (float) v;
    }

    private float calculateDistanceLowerCamera(float pitchAngle) {
        System.out.println("BOT");
        System.out.println("Pitch: " + pitchAngle);
        double v = Math.tan(Math.PI / 2 + (pitchAngle - (float) 0.52)) * 0.465;
        System.out.println("Ergebnis: " + v);
        return (float) v;
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
        System.out.println("Moving");
        System.out.println("x: "+stage.x+" y: "+stage.y);
        try {
            alMotion.moveTo(stage.x, stage.y, (float) 0);
//                        alMotion.moveTo(stage.x, stage.y, (float) 0);

        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void moveToAdjustment(){

    }


}
