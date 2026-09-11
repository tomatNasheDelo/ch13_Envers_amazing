package com.example.envers;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.envers.Audited;

import com.example.Constants;



@Entity 
@Table(name = "USERS")
@Audited
public class User {


    @Id 
    @GeneratedValue(generator = Constants.ID_GENERATOR)
    private Long id;


    @NotNull
    private String username;


    public User() {
    }


    public User(@NotNull String username) {
        this.username = username;
    }


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getUsername() {
        return username;
    }


    public void setUsername(String username) {
        this.username = username;
    }

    


    
}
