package org.torproject.onionoo;

import java.util.List;
import java.util.Map;

class WeightsDocument extends Document {

  class WeightsHistory {
    String first;
    String last;
    Integer interval;
    Double factor;
    Integer count;
    List<Integer> values;
  }

  class NodeWeights {
    String fingerprint;
    Map<String, WeightsHistory> advertised_bandwidth_fraction;
    Map<String, WeightsHistory> consensus_weight_fraction;
    Map<String, WeightsHistory> guard_probability;
    Map<String, WeightsHistory> middle_probability;
   Map<String, WeightsHistory> exit_probability;
  }

  String relays_published;
  List<NodeWeights> relays;
  String bridges_published;
  List<NodeWeights> bridges;
}

