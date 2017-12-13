package nao;

import com.aldebaran.qi.CallError;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.helper.proxies.ALMotion;
import org.opencv.core.Point;

import java.util.ArrayList;

public class MoveNao {

    ALMotion alMotion;
    float x;
    float y;

    public enum Direction {
        LEFT, RIGHT
    }

    ;

    public MoveNao(Session session) throws Exception {
        System.out.println("MOVE HEAD");
        alMotion = new ALMotion(session);
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
        //Thread.sleep(4000);
        //alMotion.setStiffnesses("Head", 0);

    }

    public void followBall(Point p, boolean foundObject) {
        this.x = (float) p.x;
        this.y = (float) p.y;
        //Mitte 160 : 120
        if (x < 150) turnRight();
        else if(x > 170) turnLeft();
    }

    private void turnLeft() {
        System.out.println("Turning Left");
        try {
            alMotion.changeAngles("HeadYaw",-0.1,(float)0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void turnRight() {
        System.out.println("Truning Right");
        try {
            alMotion.changeAngles("HeadYaw",0.1,(float)0.05);
        } catch (CallError callError) {
            callError.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
