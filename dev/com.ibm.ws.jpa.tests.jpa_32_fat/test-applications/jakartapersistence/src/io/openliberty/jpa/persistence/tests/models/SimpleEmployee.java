package io.openliberty.jpa.persistence.tests.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class SimpleEmployee {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private long salary;

    public SimpleEmployee() {
    }

    public SimpleEmployee(String name, long salary) {
        this.name = name;
        this.salary = salary;
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

    public long getSalary() {
        return salary;
    }

    public void setSalary(long salary) {
        this.salary = salary;
    }
}
