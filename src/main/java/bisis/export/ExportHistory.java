package bisis.export;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jongo.marshall.jackson.oid.MongoId;

import java.util.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ExportHistory {

    @MongoId String _id;
    private Date exportDate;
    private String exportType;
}
