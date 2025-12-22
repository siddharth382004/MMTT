package com.example.sih.db;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sms_table")
public class  SmsEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String sender;
    public String message;
    public String lat;
    public String lon;
    public String battery;
    public String signal;
    public String time;

    public SmsEntity(int id, String sender, String message, String lat, String lon, String battery, String signal, String time) {
        this.id = id;
        this.sender = sender;
        this.message = message;
        this.lat = lat;
        this.lon = lon;
        this.battery = battery;
        this.signal = signal;
        this.time = time;
    }
}