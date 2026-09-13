package com.GymGate.bussines.services;

import com.GymGate.bussines.models.ValidationResult;

public interface RecognitionListener {
    void onRecognition(ValidationResult result);
}
