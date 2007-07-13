//
// ImprovisionTiffReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;
import java.util.*;
import loci.formats.*;

/** 
 * ImprovisionTiffReader is the file format reader for 
 * Improvision TIFF files. 
 */
public class ImprovisionTiffReader extends BaseTiffReader {

  // -- Fields --

  private String[] cNames;
  private int pixelSizeT;

  // -- Constructor --

  public ImprovisionTiffReader() {
    super("Improvision TIFF", new String[] {"tif", "tiff"});
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    if (!name.toLowerCase().endsWith("tif") && 
      !name.toLowerCase().endsWith("tiff")) 
    {  
      return false; 
    } 
    
    try {
      RandomAccessStream ras = new RandomAccessStream(name);
      Hashtable ifd = TiffTools.getFirstIFD(ras);
      ras.close();
      if (ifd == null) return false;
    
      String comment = 
        (String) ifd.get(new Integer(TiffTools.IMAGE_DESCRIPTION)); 
      return comment.indexOf("Improvision") != -1; 
    }
    catch (Exception e) { return false; } 
  }
  
  // -- Internal BaseTiffReader API methods --
 
  /* @see BaseTiffReader#initStandardMetadata() */
  protected void initStandardMetadata() throws FormatException, IOException {
    super.initStandardMetadata();

    put("Improvision", "yes");
  
    // parse key/value pairs in the comment
    String comment = (String) getMeta("Comment");
    if (comment != null) {
      StringTokenizer st = new StringTokenizer(comment, "\n");
      while (st.hasMoreTokens()) {
        String line = st.nextToken();
        int equals = line.indexOf("=");
        if (equals < 0) continue;
        String key = line.substring(0, equals);
        String value = line.substring(equals + 1);
        addMeta(key, value);
      }
      metadata.remove("Comment");
    } 
 
    core.sizeZ[0] = Integer.parseInt((String) getMeta("TotalZPlanes"));
    core.sizeC[0] = Integer.parseInt((String) getMeta("TotalChannels"));
    core.sizeT[0] = Integer.parseInt((String) getMeta("TotalTimepoints"));

    // parse each of the comments to determine axis ordering

    long[] stamps = new long[ifds.length];
    int[][] coords = new int[ifds.length][3];

    cNames = new String[core.sizeC[0]];

    for (int i=0; i<ifds.length; i++) {
      comment = (String) TiffTools.getIFDValue(ifds[i], 
        TiffTools.IMAGE_DESCRIPTION);
      comment = comment.replaceAll("\r\n", "\n");
      comment = comment.replaceAll("\r", "\n");
      StringTokenizer st = new StringTokenizer(comment, "\n");
      String channelName = null; 
      while (st.hasMoreTokens()) {
        String line = st.nextToken();
        int equals = line.indexOf("=");
        if (equals < 0) continue;
        String key = line.substring(0, equals);
        String value = line.substring(equals + 1);
     
        if (key.equals("TimeStampMicroSeconds")) {
          stamps[i] = Long.parseLong(value);
        }
        else if (key.equals("ZPlane")) coords[i][0] = Integer.parseInt(value);
        else if (key.equals("ChannelNo")) {
          coords[i][1] = Integer.parseInt(value);
        } 
        else if (key.equals("TimepointName")) {
          coords[i][2] = Integer.parseInt(value);
        }
        else if (key.equals("ChannelName")) {
          channelName = value;  
        }
        else if (key.equals("ChannelNo")) {
          int ndx = Integer.parseInt(value);
          if (cNames[ndx] == null) cNames[ndx] = channelName;
        }
      }
    }
    // determine average time per plane

    long sum = 0;
    for (int i=1; i<stamps.length; i++) {
      long diff = stamps[i] - stamps[i - 1];
      if (diff > 0) sum += diff;
    }
    pixelSizeT = (int) (sum / core.sizeT[0]);

    // determine dimension order

    core.currentOrder[0] = "XY";
    for (int i=1; i<coords.length; i++) {
      int zDiff = coords[i][0] - coords[i - 1][0];
      int cDiff = coords[i][1] - coords[i - 1][1];
      int tDiff = coords[i][2] - coords[i - 1][2];
   
      if (zDiff > 0 && core.currentOrder[0].indexOf("Z") < 0) {
        core.currentOrder[0] += "Z";
      }
      if (cDiff > 0 && core.currentOrder[0].indexOf("C") < 0) {
        core.currentOrder[0] += "C";
      }
      if (tDiff > 0 && core.currentOrder[0].indexOf("T") < 0) {
        core.currentOrder[0] += "T";
      }
      if (core.currentOrder[0].length() == 5) break; 
    }

    if (core.currentOrder[0].indexOf("Z") < 0) core.currentOrder[0] += "Z";
    if (core.currentOrder[0].indexOf("C") < 0) core.currentOrder[0] += "C";
    if (core.currentOrder[0].indexOf("T") < 0) core.currentOrder[0] += "T";
  } 

  /* @see BaseTiffReader#initMetadataStore() */
  protected void initMetadataStore() {
    super.initMetadataStore();
    MetadataStore store = getMetadataStore();

    store.setPixels(new Integer(core.sizeX[0]), new Integer(core.sizeY[0]),
      new Integer(core.sizeZ[0]), new Integer(core.sizeC[0]),
      new Integer(core.sizeT[0]), new Integer(core.pixelType[0]),
      new Boolean(!core.littleEndian[0]), core.currentOrder[0], null, null);

    float fx = Float.parseFloat((String) getMeta("XCalibrationMicrons"));
    float fy = Float.parseFloat((String) getMeta("YCalibrationMicrons"));
    float fz = Float.parseFloat((String) getMeta("ZCalibrationMicrons"));

    store.setDimensions(new Float(fx), new Float(fy), new Float(fz),
      null, new Float(pixelSizeT / 1000000.0), null);
  }

}
