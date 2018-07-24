package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history_old.TimeStampedRecord;

/**
 * Created by geoff on 6/11/16.
 */
public class AlarmClockReminderPumpEvent extends TimeStampedRecord {

    public AlarmClockReminderPumpEvent() {
    }


    @Override
    public String getShortTypeName() {
        return "Alarm Reminder";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
