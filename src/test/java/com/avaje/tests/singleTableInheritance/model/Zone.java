package com.avaje.tests.singleTableInheritance.model;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

@Entity
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="type", discriminatorType=DiscriminatorType.STRING)
@Table(name="zones")
public class Zone
{
	@Id
	@Column(name="ID")
	private Integer id;

	public Integer getId()
	{
		return id;
	}

	public void setId(Integer id)
	{
		this.id = id;
	}
	
	public int hashCode() {
		if (getId() != null) return getId().hashCode();
		else return super.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Zone) {
			return ((Zone) obj).getId().equals(getId());
		} else {
			return false;
		}
	}
}
