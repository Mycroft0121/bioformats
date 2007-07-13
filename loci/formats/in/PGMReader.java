//
// PGMReader.java
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.StringTokenizer;
import loci.formats.*;

/**
 * PGMReader is the file format reader for Portable Gray Map (PGM) images.
 *
 * Much of this code was adapted from ImageJ (http://rsb.info.nih.gov/ij). 
 */
public class PGMReader extends FormatReader {

  // -- Fields --

  private boolean rawBits;
 
  /** Offset to pixel data. */
  private long offset;

  // -- Constructor --

  /** Constructs a new PGMReader. */
  public PGMReader() { super("Portable Gray Map", "pgm"); }

  // -- IFormatReader API methods -- 

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    return block[0] == 'P';
  }

  /* @see loci.formats.IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    byte[] buf = new byte[core.sizeX[0] * core.sizeY[0] * core.sizeC[0] *
      FormatTools.getBytesPerPixel(core.pixelType[0])]; 
    return openBytes(no, buf); 
  }

  /* @see loci.formats.IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    if (no < 0 || no >= getImageCount()) {
      throw new FormatException("Invalid image number: " + no);
    }
    if (buf.length < core.sizeX[0] * core.sizeY[0] * core.sizeC[0] *
      FormatTools.getBytesPerPixel(core.pixelType[0]))
    {
      throw new FormatException("Buffer too small.");
    }
    
    in.seek(offset); 
    if (rawBits) in.read(buf);
    else {
      int pt = 0; 
      while (pt < buf.length) {
        String line = in.readLine().trim();
        line = line.replaceAll("[^0-9]", " ");
        StringTokenizer t = new StringTokenizer(line, " ");
        while (t.hasMoreTokens()) {
          int q = Integer.parseInt(t.nextToken().trim()); 
          if (core.pixelType[0] == FormatTools.UINT16) {
            short s = (short) q;
            buf[pt] = (byte) ((s & 0xff00) >> 8);
            buf[pt + 1] = (byte) (s & 0xff);
            pt += 2;
          }
          else {
            buf[pt] = (byte) q;
            pt++;
          }
        }
      } 
    }

    return buf;
  }

  /* @see loci.formats.IFormatReader#openImage(int) */
  public BufferedImage openImage(int no) throws FormatException, IOException {
    return ImageTools.makeImage(openBytes(no), core.sizeX[0], core.sizeY[0],
      core.sizeC[0], core.interleaved[0], 
      FormatTools.getBytesPerPixel(core.pixelType[0]), core.littleEndian[0]);
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    in = new RandomAccessStream(id);
  
    String magic = in.readLine().trim();

    boolean isBlackAndWhite = false;

    rawBits = magic.equals("P4") || magic.equals("P5") || magic.equals("P6");
    core.sizeC[0] = (magic.equals("P3") || magic.equals("P6")) ? 3 : 1;
    isBlackAndWhite = magic.equals("P1") || magic.equals("P4");

    String line = in.readLine().trim();
    while (line.startsWith("#") || line.length() == 0) line = in.readLine();
  
    line = line.replaceAll("[^0-9]", " ");
    core.sizeX[0] = 
      Integer.parseInt(line.substring(0, line.indexOf(" ")).trim());
    core.sizeY[0] = 
      Integer.parseInt(line.substring(line.indexOf(" ") + 1).trim());
  
    if (!isBlackAndWhite) {
      int max = Integer.parseInt(in.readLine().trim());
      if (max > 255) core.pixelType[0] = FormatTools.UINT16;
      else core.pixelType[0] = FormatTools.UINT8;
    }
  
    offset = in.getFilePointer(); 
    
    core.rgb[0] = core.sizeC[0] == 3;
    core.currentOrder[0] = "XYCZT";
    core.littleEndian[0] = true;
    core.interleaved[0] = false;
    core.sizeZ[0] = 1; 
    core.sizeT[0] = 1; 
    core.imageCount[0] = 1; 
  
    MetadataStore store = getMetadataStore();
    store.setPixels(new Integer(core.sizeX[0]), new Integer(core.sizeY[0]),
      new Integer(core.sizeZ[0]), new Integer(core.sizeC[0]),
      new Integer(core.sizeT[0]), new Integer(core.pixelType[0]),
      new Boolean(!core.littleEndian[0]), core.currentOrder[0], null, null);
    
    for (int i=0; i<core.sizeC[0]; i++) {
      store.setLogicalChannel(i, null, null, null, null,
        core.sizeC[0] == 1 ? "monochrome" : "RGB", null, null);
    }
  }

}
