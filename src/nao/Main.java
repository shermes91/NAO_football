package nao;

import com.aldebaran.qi.CallError;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.opencv.core.Core;
import sun.plugin2.applet.FXAppletSecurityManager;

public class Main extends Application {
    VideoController controller;

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("sample.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();

        controller = loader.getController();
    }

    @Override
    public void stop() throws CallError, InterruptedException {
        controller.shutdown();
        System.out.println("Stage is closing");
        System.exit(0);
    }

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        launch(args);
    }
}
