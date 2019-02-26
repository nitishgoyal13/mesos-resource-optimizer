package com.optimizer.services;

import org.json.JSONArray;
import org.json.JSONObject;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class GrafanaService {

    public static int getValueFromGrafanaResponse(String response) {
        JSONObject jsonObject = new JSONObject(response);
        if(jsonObject.has("series") && ((JSONArray)jsonObject.get("series")).length() > 0 && ((JSONObject)((JSONArray)jsonObject.get(
                "series")).get(0)).has("values") && ((JSONArray)((JSONObject)((JSONArray)jsonObject.get("series")).get(0)).get(
                "values")).length() > 0 && ((JSONArray)((JSONArray)((JSONObject)((JSONArray)jsonObject.get("series")).get(0)).get(
                "values")).get(0)).length() > 1) {
            return (int)((JSONArray)((JSONArray)((JSONObject)((JSONArray)jsonObject.get("series")).get(0)).get("values")).get(0)).get(1);
        }
        //TODO Rename this to something meaningful
        return -1;
    }
}
