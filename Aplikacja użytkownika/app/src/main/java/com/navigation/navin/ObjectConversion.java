package com.navigation.navin;

public class ObjectConversion {
    private double x;
    private double y;
    private double z;
    private String room;
    private long type;

    private ObjectConversion(){
        this.x=0.0;
        this.y=0.0;
        this.z=0.0;
        this.room="empty";
        this.type=0;

    }

    public Double getX() {
        return x;
    }

    public Double getY() {
        return y;
    }

    public Double getZ() {
        return z;
    }

    public String getRoom() {
        return room;
    }

    public long getType() {
        return type;
    }

}
