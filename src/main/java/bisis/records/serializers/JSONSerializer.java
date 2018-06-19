package bisis.records.serializers;

import bisis.records.Record;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


public class JSONSerializer {

  public static String toJSON(Record record) {
    try {
      return mapper.writeValueAsString(record);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return "";
    }
  }
  
  private static ObjectMapper mapper = new ObjectMapper();
  
  static {
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }
}
