package com.GymGate.bussines.db.entities;


import java.util.List;

public class MemberEmbedding {
    private int id;
    private List<float[]>  embedding;

    public MemberEmbedding(int id, List<float[]>  embedding){
        this.id=id;
        this.embedding=embedding;
    }

    public int getId() {
        return id;
    }

    public List<float[]> getEmbeddings(){
        return embedding;
    }

}
