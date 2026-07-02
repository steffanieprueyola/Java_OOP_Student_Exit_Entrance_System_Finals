package com.campus;

/**
 * Student model — mirrors the students table.
 */
public class Student {

    private String studentId;
    private String fullName;
    private String sex;          // "M" or "F"
    private boolean blockedNextDay; // true if auto-timed-out at 10 PM

    public Student() {}

    public Student(String studentId, String fullName, String sex) {
        this.studentId = studentId;
        this.fullName  = fullName;
        this.sex       = sex;
    }

    public Student(String studentId, String fullName, String sex, boolean blockedNextDay) {
        this.studentId      = studentId;
        this.fullName       = fullName;
        this.sex            = sex;
        this.blockedNextDay = blockedNextDay;
    }

    // ── Getters & Setters ─────────────────────────────────────────────

    public String getStudentId()             { return studentId; }
    public void   setStudentId(String v)     { this.studentId = v; }

    public String getFullName()              { return fullName; }
    public void   setFullName(String v)      { this.fullName = v; }

    public String getSex()                   { return sex; }
    public void   setSex(String v)           { this.sex = v; }

    public boolean isBlockedNextDay()        { return blockedNextDay; }
    public void    setBlockedNextDay(boolean v) { this.blockedNextDay = v; }

    @Override
    public String toString() {
        return studentId + " - " + fullName;
    }
}
