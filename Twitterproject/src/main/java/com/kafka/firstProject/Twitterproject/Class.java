package com.kafka.firstProject.Twitterproject;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="")
public class Class {

	private List<Student> student;

	public Class() {
	}

	public Class(List<Student> student) {
		super();
		this.student = student;
	}
	@XmlElement
	public List<Student> getStudent() {
		return student;
	}

	public void setStudent(List<Student> student) {
		this.student = student;
	}

}
