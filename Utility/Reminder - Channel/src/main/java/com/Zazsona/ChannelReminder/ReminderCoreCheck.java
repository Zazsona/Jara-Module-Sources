package com.Zazsona.ChannelReminder;

import com.Zazsona.ReminderCore.ReminderManager;
import module.Load;
import org.slf4j.LoggerFactory;

public class ReminderCoreCheck extends Load
{
    @Override
    public void load()
    {
        try
        {
            Class checkClass = ReminderManager.class;
        }
        catch (NoClassDefFoundError e)
        {
            LoggerFactory.getLogger(getClass()).error("Reminder Core is missing. Channel reminders WILL NOT work.");
        }
    }
}
