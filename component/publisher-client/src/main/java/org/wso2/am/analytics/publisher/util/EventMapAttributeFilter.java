package org.wso2.am.analytics.publisher.util;

import org.wso2.am.analytics.publisher.exception.MetricReportingException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EventMapAttributeFilter {
    private static  final EventMapAttributeFilter INSTANCE = new EventMapAttributeFilter();

    public static EventMapAttributeFilter getInstance(){
        return INSTANCE;
    }

    public Map<String,Object> filter(Map<String,Object> source, Map<String,Class> requiredAttributes) throws  MetricReportingException{

        Set<String> targetKeys = requiredAttributes.keySet();
        Map<String,Object> filteredEventMap = new HashMap<>();
        if(validateRequiredAttributes(source,requiredAttributes)) {
            for (String key : targetKeys) {
                filteredEventMap.put(key, source.get(key));
            }
        }

        return filteredEventMap;
    }

    private static boolean validateRequiredAttributes(Map<String,Object> source, Map<String,Class> requiredAttributes) throws MetricReportingException {

        for (Map.Entry<String, Class> entry : requiredAttributes.entrySet()) {
            Object attribute = source.get(entry.getKey());
            if (attribute == null) {
                throw new MetricReportingException(entry.getKey() + " is set as required attribute but missing in metric data. This metric event "
                        + "will not be processed further.");
            } else if (!attribute.getClass().equals(entry.getValue())) {
                throw new MetricReportingException(entry.getKey() + " is expecting a " + entry.getValue() + " type "
                        + "required attribute while attribute of type "
                        + attribute.getClass() + " is present.");
            }
        }
        return true;
    }

}
