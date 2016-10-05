package at.nineyards;

import org.joda.time.DateTime;

import java.util.Date;

/**
 * Created by Peter on 05.10.2016.
 */
public interface TimeframeCallback {

    void onTimeFrameSet(DateTime timeStarting, DateTime timeEnding);

    void onExit();
}
