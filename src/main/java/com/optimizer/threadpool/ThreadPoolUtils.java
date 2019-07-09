package com.optimizer.threadpool;

import static com.optimizer.util.OptimizerUtils.ExtractionStrategy;
import static com.optimizer.util.OptimizerUtils.INDEX_ZERO;
import static com.optimizer.util.OptimizerUtils.SERIES;
import static com.optimizer.util.OptimizerUtils.VALUES;
import static com.optimizer.util.OptimizerUtils.getArrayFromJSONObject;
import static com.optimizer.util.OptimizerUtils.getAvgValueFromJsonArray;
import static com.optimizer.util.OptimizerUtils.getMaxValueFromJsonArray;
import static com.optimizer.util.OptimizerUtils.getObjectFromJSONArray;

import org.json.JSONArray;
import org.json.JSONObject;

/***
 Created by nitish.goyal on 28/03/19
 ***/
public class ThreadPoolUtils {

    public static long getValueFromGrafanaResponse(String response, ExtractionStrategy extractionStrategy) {
        JSONObject jsonObject = new JSONObject(response);
        JSONArray seriesJSONArray = getArrayFromJSONObject(jsonObject, SERIES);
        JSONObject seriesJSONObject = getObjectFromJSONArray(seriesJSONArray, INDEX_ZERO);
        JSONArray valuesJSONArray = getArrayFromJSONObject(seriesJSONObject, VALUES);
        switch (extractionStrategy) {
            case MAX:
                return getMaxValueFromJsonArray(valuesJSONArray);
            case AVERAGE:
                return getAvgValueFromJsonArray(valuesJSONArray);
            default:
                return getMaxValueFromJsonArray(valuesJSONArray);
        }

    }

    public static long getValueFromGrafanaResponseByHost(String response, ExtractionStrategy extractionStrategy) {
        JSONArray valuesJSONArray = getArrayFromJSONObject(new JSONObject(response), VALUES);
        switch (extractionStrategy) {
            case MAX:
                return getMaxValueFromJsonArray(valuesJSONArray);
            case AVERAGE:
                return getAvgValueFromJsonArray(valuesJSONArray);
            default:
                return getMaxValueFromJsonArray(valuesJSONArray);
        }

    }

}
