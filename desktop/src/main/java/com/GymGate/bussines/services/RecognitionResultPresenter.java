package com.GymGate.bussines.services;


import com.GymGate.bussines.models.ValidationResult;
import javafx.application.Platform;

public class RecognitionResultPresenter {

    private static RecognitionResultPresenter instance;
    private final RecognitionListener mainRecognitionListener;
    private final RecognitionListener secondayRecognitionListener;


    public void show(ValidationResult result){
          Platform.runLater(()->{
              mainRecognitionListener.onRecognition(result);
              secondayRecognitionListener.onRecognition(result);
          });
    }

    private RecognitionResultPresenter(RecognitionListener mainRecognitionListener,RecognitionListener secondayRecognitionListener){
        this.mainRecognitionListener= mainRecognitionListener;
        this.secondayRecognitionListener=secondayRecognitionListener;
    }

    public static RecognitionResultPresenter getInstance(){
        return instance;
    }

    public static void init(RecognitionListener mainRecognitionListener,RecognitionListener secondayRecognitionListener){
        if(instance!=null) return;
        instance=new RecognitionResultPresenter(mainRecognitionListener,secondayRecognitionListener);
    }

}
