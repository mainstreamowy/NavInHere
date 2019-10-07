package com.navigation.navin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Workers {
    public String degree;
    public String name_;
    public String surename;
    public String phone_no;
    public List <Consultations> consultations = new ArrayList<>();

    public List<Consultations> getConsultations() {
        return consultations;
    }

    public void setConsultations(List<Consultations> consultations) {
        this.consultations = consultations;
    }

    public Workers(){

    }


    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getName_() {
        return name_;
    }

    public void setName_(String name_) {
        this.name_ = name_;
    }

    public String getSurename() {
        return surename;
    }

    public void setSurename(String surename) {
        this.surename = surename;
    }

    public String getPhone_no() {
        return phone_no;
    }

    public void setPhone_no(String phone_no) {
        this.phone_no = phone_no;
    }

}
