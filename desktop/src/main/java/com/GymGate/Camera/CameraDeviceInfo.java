package com.GymGate.Camera;

 class CameraDeviceInfo {

    private final String name;
    private final int index;

    public CameraDeviceInfo(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String getName() { return name; }
    public int getIndex() { return index; }

    @Override
    public String toString() {
        return name + " (index " + index + ")";
    }
}