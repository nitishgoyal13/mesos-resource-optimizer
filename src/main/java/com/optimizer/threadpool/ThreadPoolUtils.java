package com.optimizer.threadpool;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by nitish.goyal on 28/03/19
 ***/
public class ThreadPoolUtils {

    public static int getValueFromGrafanaResponse(String response, ExtractionStrategy extractionStrategy) {
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

    public static int getValueFromGrafanaResponseByHost(String response, ExtractionStrategy extractionStrategy) {
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
