package crunch;

import java.io.IOException;
import org.apache.crunch.DoFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.PCollection;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.fn.Aggregators;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.lib.join.JoinStrategy;
import org.apache.crunch.lib.join.JoinType;
import org.apache.crunch.lib.join.MapsideJoinStrategy;
import org.junit.Test;

import static org.apache.crunch.types.writable.Writables.ints;
import static org.apache.crunch.types.writable.Writables.strings;
import static org.apache.crunch.types.writable.Writables.tableOf;

// cf. MaxTemperatureByStationNameUsingDistributedCacheFile
public class MaxTemperatureByStationNameCrunchTest {
  
  @Test
  public void test() throws IOException {
    Pipeline pipeline = new MRPipeline(MaxTemperatureByStationNameCrunchTest.class);

    PCollection<String> records = pipeline.readTextFile("input/ncdc/all");
    PCollection<String> stations = pipeline.readTextFile
        ("input/ncdc/metadata/stations-fixed-width.txt");

    PTable<String, Integer> maxTemps = records
        .parallelDo(toStationIdTempPairsFn(), tableOf(strings(), ints()))
        .groupByKey()
        .combineValues(Aggregators.MAX_INTS());
    PTable<String, String> stationIdToName = stations
        .parallelDo(toStationIdNamePairsFn(), tableOf(strings(), strings()));

    JoinStrategy<String, Integer, String> mapsideJoin =
        new MapsideJoinStrategy<String, Integer, String>();
    PTable<String, Pair<Integer, String>> joined =
        mapsideJoin.join(maxTemps, stationIdToName, JoinType.INNER_JOIN);
    pipeline.writeTextFile(joined, "output");
    pipeline.run();
  }

  private static DoFn<String, Pair<String, Integer>> toStationIdTempPairsFn() {
    return new DoFn<String, Pair<String, Integer>>() {
      NcdcRecordParser parser = new NcdcRecordParser();
      @Override
      public void process(String input, Emitter<Pair<String, Integer>> emitter) {
        parser.parse(input);
        if (parser.isValidTemperature()) {
          emitter.emit(Pair.of(parser.getStationId(), parser.getAirTemperature()));
        }
      }
    };
  }
  private static DoFn<String, Pair<String, String>> toStationIdNamePairsFn() {
    return new DoFn<String, Pair<String, String>>() {
      NcdcStationMetadataParser parser = new NcdcStationMetadataParser();
      @Override
      public void process(String input, Emitter<Pair<String, String>> emitter) {
        if (parser.parse(input)) {
          emitter.emit(Pair.of(parser.getStationId(), parser.getStationName()));
        }
      }
    };
  }

}
