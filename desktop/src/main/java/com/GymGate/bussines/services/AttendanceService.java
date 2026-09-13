package com.GymGate.bussines.services;

import com.GymGate.bussines.db.dao.AttendanceDao;
import com.GymGate.bussines.db.entities.Attendance;
import com.GymGate.bussines.models.AttendanceRecord;
import com.GymGate.bussines.models.LatestAttendance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AttendanceService {

    private static AttendanceService instance;
    private final AttendanceDao attendanceDao;

    public AttendanceService(){
        this.attendanceDao=AttendanceDao.getInstance();
    }


    public void save(int id){
        attendanceDao.insert(new Attendance(id, LocalDateTime.now()));
    }

    public boolean existsToday(int id){
        return attendanceDao.existToday(id);
    }

    public List<AttendanceRecord> find(Integer year,Integer month,Integer day){
        return attendanceDao.find(year,month,day);
    }

    public java.util.Map<Integer,Integer> countByDay(int year,int month){
        return attendanceDao.countByDay(year,month);
    }

    public List<LocalDateTime> findTimestamps(int year,int month){
        return attendanceDao.findTimestamps(year,month);
    }

    public java.util.Map<Integer,Integer> countByMonth(int year){
        return attendanceDao.countByMonth(year);
    }

    public java.util.SortedMap<Integer,Integer> countByYear(){
        return attendanceDao.countByYear();
    }

    public synchronized static AttendanceService getInstance(){
        if(instance==null){
            instance=new AttendanceService();
        }
            return instance;
    }
}
