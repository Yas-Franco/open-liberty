package io.openliberty.jpa.persistence.tests.models;

public class EmployeeSalaryDTO {
    private final long salary;
    private final long adjustedSalary;

    public EmployeeSalaryDTO(long salary, long adjustedSalary) {
        this.salary = salary;
        this.adjustedSalary = adjustedSalary;
    }

    public long salary() {
        return salary;
    }

    public long adjustedSalary() {
        return adjustedSalary;
    }

    @Override
    public String toString() {
        return "EmployeeSalaryDTO{salary=" + salary + ", adjustedSalary=" + adjustedSalary + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;
        EmployeeSalaryDTO that = (EmployeeSalaryDTO) o;
        return salary == that.salary && adjustedSalary == that.adjustedSalary;
    }

    @Override
    public int hashCode() {
        return (int) (31 * salary + adjustedSalary);
    }
}
