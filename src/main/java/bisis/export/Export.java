package bisis.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

import bisis.records.Record;
import bisis.records.serializers.PrimerakSerializer;
import bisis.records.serializers.UnimarcSerializer;
import bisis.utils.DateUtils;
import bisis.utils.ZipUtils;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;

public class Export {
  public static void main(String[] args) {
    Options options = new Options();
    options.addOption("a", "address", true,
        "MongoDB server address (default: localhost)");
    options.addOption("p", "port", true, "Mongodb server port (default: 27017)");
    options.addOption("d", "database", true,
        "MongoDB database name (default: bisis)");
    options.addOption("u", "username", true,
        "MongoDB server username (default: bisis)");
    options.addOption("w", "password", true,
        "MongoDB server password (default: bisis)");
    options.addOption("o", "output", true, "Output directory");
    options.addOption("i", "incremental", false, "Indicates incremental export");
    CommandLineParser parser = new GnuParser();
    String address = "localhost";
    int port = 27017;
    String database = "bisis";
    String username = "bisis";
    String password = "bisis";
    String outputDir = "";
    boolean incremental = false;
    
    Date today = new Date();
    Date startDate = null;
    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy.");
    String zipName = "bgb-centralna.zip";
    
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("a"))
        address = cmd.getOptionValue("a");
      if (cmd.hasOption("p"))
        port = Integer.parseInt(cmd.getOptionValue("p"));
      if (cmd.hasOption("d"))
        database = cmd.getOptionValue("d");
      if (cmd.hasOption("u"))
        username = cmd.getOptionValue("u");
      if (cmd.hasOption("w"))
        password = cmd.getOptionValue("w");
      if (cmd.hasOption("o"))
        outputDir = cmd.getOptionValue("o");
      else
        throw new Exception("Output directory not specified.");
      if (cmd.hasOption("i"))
        incremental = true;
    } catch (Exception ex) {
      System.err.println("Invalid parameter(s), reason: " + ex.getMessage());
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("bisis5-export", options);
      return;
    }
    try {
      File dir = new File(outputDir);
      if (!dir.exists()) {
        dir.mkdirs();
        System.out.println("Directory " + outputDir + " is created.");
      }
      if (!dir.canWrite()) {
        throw new Exception("Directory " + outputDir + " is not writeable.");
      }
      File zipFile = new File(dir, zipName);
      if (zipFile.exists()) {
        zipFile.delete();
        System.out.println("File " + zipName + 
            " already exists, old version is deleted.");
      }
      File recordsFile = new File(dir, "records.dat");
      if (recordsFile.exists())
        recordsFile.delete();
      File infoFile = new File(dir, "export.info");
      if (infoFile.exists())
        infoFile.delete();

      RandomAccessFile out2 = null;
      out2 = new RandomAccessFile(recordsFile, "rw");
      DB db = new MongoClient(address, port).getDB(database);
      Jongo jongo = new Jongo(db);

      MongoCollection centralRecsCollection = jongo.getCollection("bgb_records");
      ExportHistory lastExport = null;
      MongoCollection exportHistoryCollection = jongo.getCollection("bgb_export_history");

      if (incremental) {


        try {
          lastExport = exportHistoryCollection.find().sort("{exportDate: -1}").as(ExportHistory.class).next();
          startDate = lastExport.getExportDate();
          System.out.println("Incremental export from date: " + sdf.format(startDate));
        }
        catch (Exception e) {
          System.out.println("No previous export, doing full export.");
          ExportHistory newExportHistory = new ExportHistory();
          newExportHistory.setExportDate(new Date());
          newExportHistory.setExportType("F");
          exportHistoryCollection.save(newExportHistory);
          startDate = newExportHistory.getExportDate();
          incremental = false;
          //e.printStackTrace();
        }
      }

      MongoCursor<Record> recs = null;
      if (incremental) {
        recs = centralRecsCollection.find("{$or: [{'creationDate': {$gte: # }}," +
                "  {'lastModifiedDate': {$gte: # }}]}", startDate, startDate).as(Record.class);
      }
      else {
        recs = centralRecsCollection.find().as(Record.class);
      }

      //proveriti da li treba da upise danas
      Date startToday = DateUtils.getStartOfDay(new Date());
      ExportHistory alreadyExportedToday = exportHistoryCollection.findOne("{'exportDate': {$gte:#}}", startDate).as(ExportHistory.class);
      if (alreadyExportedToday == null) {
        ExportHistory newestExportHistory = new ExportHistory();
        newestExportHistory.setExportType(incremental ? "I" : "F");
        newestExportHistory.setExportDate(new Date());
        exportHistoryCollection.save(newestExportHistory);
      }
      else {
        System.out.println("Export for today already created!");
      }


      System.out.println("Found " + recs.count() + " records in the database.");
      System.out.println("Dumping to " + zipFile);
      int i = 0;
      if (recs != null && recs.count() > 0){
        while(recs.hasNext()) {
          Record rec = recs.next();
          rec.pack();
        if (rec.getFieldCount() > 0) {
          rec = PrimerakSerializer.metapodaciUPolje000(rec);
          out2.writeBytes(UnimarcSerializer.toUNIMARC(0, rec, true).replace('\n', '\t'));
          out2.writeBytes("\n");
          if (i > 0 && i % 1000 == 0)
            System.out.println(Integer.toString(i) + " records dumped.");
          i++;
          }
        }
      }
      else {
        System.out.println("0 records found!");
      }

      System.out.println("Total " + Integer.toString(i) + " records dumped.");
      out2.close();

      ExportInfo exportInfo = new ExportInfo();
      exportInfo.setExportDate(today);
      exportInfo.setExportType(incremental ? ExportInfo.TYPE_INCREMENTAL : ExportInfo.TYPE_FULL);
      if (incremental)
        exportInfo.setStartDate(startDate);
      exportInfo.setRecordCount(i);
      BufferedWriter info = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(infoFile), "UTF8"));
      info.write(exportInfo.toString());
      info.close();
      
      ZipUtils.zip(zipFile, new File[] { recordsFile, infoFile} );
      recordsFile.delete();
      infoFile.delete();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
