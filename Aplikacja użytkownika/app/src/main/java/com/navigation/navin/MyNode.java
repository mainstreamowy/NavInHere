package com.navigation.navin;

import android.util.Log;

import com.google.ar.sceneform.Node;

import java.util.ArrayList;
import java.util.List;

public class MyNode {

    public Node node;
    public List <MyNode> neighbours = new ArrayList<>();
    public float fCost;
    public float hCost;
    public float gCost;
    public float cost;
    public String room_no;
    double inf = Double.POSITIVE_INFINITY;

    public MyNode(){
        this.gCost = (float)inf;
        this.fCost = (float)inf;
        this.room_no = "none";
    }

    public String getRoom_no() {
        return room_no;
    }

    public void setRoom_no(String room_no) {
        this.room_no = room_no;
    }

    public void setfCost(float fCost) {
        this.fCost = fCost;
    }

    public void setgCost(float gCost) {
        this.gCost = gCost;
    }

    public void sethCost(float hCost) {
        this.hCost = hCost;
    }

    public void setCost(float cost) {
        this.cost = cost;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public float getgCost() {
        return this.gCost;
    }

    public float getfCost() {
        return this.fCost;
    }

    public float gethCost() {
        return this.hCost;
    }

    public float getCost() {
        return this.cost;
    }

    public Node getNode() {
        return node;
    }

    public List<MyNode> getNeighbours() {

        return neighbours;
    }

    public void addNeighbours(MyNode neighbour)
    {
        this.neighbours.add(neighbour);
    }

    public void setNeighbours(List <MyNode> neighbours) {
        this.neighbours = neighbours;
    }
}
