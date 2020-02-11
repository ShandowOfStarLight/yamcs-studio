package org.yamcs.studio.core.model;

import org.yamcs.protobuf.AlarmData;

/**
 * Reports on alarms on the studio-wide processor
 */
public interface AlarmListener {

    void processAlarmData(AlarmData alarmData);
}
