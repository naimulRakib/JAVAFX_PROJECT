package com.scholar;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication(scanBasePackages = "com.scholar") // 🌟 ১. পুরো প্যাকেজ স্ক্যান করার জন্য প্রস্তুত
public class Main extends Application {

    public static HostServices hostServices;
    
    // Spring Boot-এর সেশন বা কনটেক্সট ধরে রাখার জন্য
    private ConfigurableApplicationContext springContext;

    @Override
    public void init() throws Exception {
        // 🟢 ২. UI লোড হওয়ার আগেই ব্যাকগ্রাউন্ডে Spring Boot চালু হবে
        // এটি আপনার সব @Service এবং @Autowired বিনগুলোকে তৈরি করে রাখবে
        springContext = SpringApplication.run(Main.class);
    }

    @Override
    public void start(Stage stage) throws IOException {
        // ৩. আপনার অরিজিনাল লজিক: HostServices সেট করা
        hostServices = getHostServices();

        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/scholar/view/login.fxml"));
        
        // 🌟 ৪. সবচাইতে গুরুত্বপূর্ণ লাইন (ম্যাজিক লাইন):
        // এটি নিশ্চিত করে যে FXML লোড হওয়ার সময় স্প্রিং বুট যেন কন্ট্রোলারগুলোকে হ্যান্ডেল করে।
        // ফলে কন্ট্রোলারের ভেতর @Autowired কাজ করবে।
        fxmlLoader.setControllerFactory(springContext::getBean);

        // আপনার অরিজিনাল সিন এবং স্টেজ লজিক (অক্ষত)
        Scene scene = new Scene(fxmlLoader.load(), 500, 600);
        
        stage.setTitle("Study Easy - Login");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        // 🟢 ৫. অ্যাপ বন্ধ করার সময় রিসোর্স ক্লিনআপ
        springContext.close();
        Platform.exit();
    }

    public static void main(String[] args) {
        // জাভা-এফএক্স লঞ্চারের মাধ্যমে অ্যাপ্লিকেশন শুরু
        launch(args);
    }
}