package com.example.envers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;

import org.hibernate.envers.NotAudited;

import com.example.Constants;



@Entity 
@org.hibernate.envers.Audited
public class Item {



    @Id 
    @GeneratedValue(generator = Constants.ID_GENERATOR)
    private Long id;


    @NotNull
    private String name;


    @OneToMany(mappedBy = "item")
    @NotAudited
    private Set<Bid> bids = new HashSet<>();


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( name = "SELLER_ID", nullable = false)
    private User seller;


    public Item() {
    }


    public Item(@NotNull String name, User seller) {
        this.name = name;
        this.seller = seller;
    }


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    public Set<Bid> getBids() {
        return Collections.unmodifiableSet(bids);
    }


    public void addBid(Bid bid) {
        bids.add(bid);
    }


    public User getSeller() {
        return seller;
    }


    public void setSeller(User seller) {
        this.seller = seller;
    }

    
    
    
}
