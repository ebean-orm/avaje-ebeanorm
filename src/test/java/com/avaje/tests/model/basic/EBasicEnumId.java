package com.avaje.tests.model.basic;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.avaje.ebean.annotation.EnumValue;

@Entity
@Table(name="e_basic_enum_id")
public class EBasicEnumId {
    
	public enum Status {
        @EnumValue("N")
        NEW,
        
        @EnumValue("A")
        ACTIVE,
        
        @EnumValue("I")
        INACTIVE,
    }
    
    @Id 
    Status status;

    String name;

    String description;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
