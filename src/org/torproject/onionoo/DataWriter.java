package org.torproject.onionoo;

public interface DataWriter {

  public void updateStatuses();

  public void updateDocuments();

  public String getStatsString();
}

