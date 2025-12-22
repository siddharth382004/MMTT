package com.example.sih.db;
import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SmsEntity.class}, version = 2, exportSchema = false)
public abstract class SmsDatabase extends RoomDatabase {
    public abstract SmsDao smsDao();
    private static volatile SmsDatabase INSTANCE;

    public static SmsDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (SmsDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            SmsDatabase.class, "sms_logger_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}


