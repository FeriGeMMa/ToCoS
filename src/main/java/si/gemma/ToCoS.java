package si.gemma;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIExportFormatDesc;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

public class ToCoS {

  private static class PointComparator implements Comparator<Vector3f> {
    private final double epsilon;

    public PointComparator(double epsilon) {
      this.epsilon = epsilon;
    }

    @Override
    public int compare(Vector3f p1, Vector3f p2) {
      if (Math.abs(p1.x - p2.x) > epsilon) {
        return Double.compare(p1.x, p2.x);
      } else if (Math.abs(p1.y - p2.y) > epsilon) {
        return Double.compare(p1.y, p2.y);
      } else if (Math.abs(p1.z - p2.z) > epsilon) {
        return Double.compare(p1.z, p2.z);
      } else {
        return 0;
      }
    }
  }

  private static class IEdgeComparator implements Comparator<IEdge> {

    @Override
    public int compare(IEdge e1, IEdge e2) {

      if (e1.a != e2.a) {
        return Integer.compare(e1.a, e2.a);
      } else if (e1.b != e2.b) {
        return Integer.compare(e1.b, e2.b);
      }

      return 0;
    }
  }

  private static class Nodify3d {

    private Map<Vector3f, Integer> lut =
        new TreeMap<Vector3f, Integer>(new PointComparator(0.00001));

    private Map<Integer, Vector3f> lut_ = new HashMap<Integer, Vector3f>();

    public int encode(Vector3f v) {
      Integer idx = lut.computeIfAbsent(v, key -> lut.size());
      lut_.put(idx, v);
      return idx;
    }

    public Vector3f decode(int n) {

      Vector3f v = lut_.get(n);

      return v;
    }

    public int size() {
      return lut.size();
    }
  }

  @EqualsAndHashCode
  @ToString
  public static class IEdge {
    private int a;
    private int b;

    public IEdge(int a, int b) {
      set(a, b);
    }

    private void set(int a, int b) {
      this.a = Math.min(a, b);
      this.b = Math.max(a, b);
    }

    public int getCommon(IEdge e) {
      if (a == e.a || a == e.b) {
        return a;
      } else if (b == e.a || b == e.b) {
        return b;
      }
      return -1;
    }

    public boolean touches(IEdge e) {
      if (a == e.a || a == e.b) {
        return true;
      } else if (b == e.a || b == e.b) {
        return true;
      }
      return false;
    }
  }

  @ToString
  @EqualsAndHashCode(of = {"ab", "bc", "ca"})
  private static class ITri {
    private IEdge _ab;
    private IEdge _bc;
    private IEdge _ca;

    private final IEdge ab;
    private final IEdge bc;
    private final IEdge ca;

    private boolean visited = false;
    private boolean removed = false;

    public ITri(IEdge ab, IEdge bc, IEdge ca) {
      super();

      this._ab = ab;
      this._bc = bc;
      this._ca = ca;

      List<IEdge> edges = Arrays.asList(ab, bc, ca);
      edges.sort(new IEdgeComparator());

      this.ab = edges.get(0);
      this.bc = edges.get(1);
      this.ca = edges.get(2);
    }

    public void reverse() {
      IEdge tmp = _ab;
      _ab = _ca;
      _ca = tmp;
    }
  }

  public static File triOptStack(File objFile) throws FileNotFoundException, IOException {
    Map<IEdge, List<ITri>> edgeTriLut = new HashMap<IEdge, List<ITri>>();
    Map<Integer, List<IEdge>> nodeEdgeLut = new HashMap<Integer, List<IEdge>>();
    int triCount = 0;
    List<ITri> tris = new ArrayList<>();
    Nodify3d nodify = new Nodify3d();

    Obj3d obj = Obj3d.read(objFile);

    triCount = obj.getNumFaces();

    for (int i = 0; i < triCount; i++) {
      Obj3dFace face = obj.getFace(i);
      int a = face.getVertexIndex(0);
      int b = face.getVertexIndex(1);
      int c = face.getVertexIndex(2);

      Vector3f tria = obj.getVertex(a);
      Vector3f trib = obj.getVertex(b);
      Vector3f tric = obj.getVertex(c);

      IEdge ab = new IEdge(nodify.encode(tria), nodify.encode(trib));
      IEdge bc = new IEdge(nodify.encode(trib), nodify.encode(tric));
      IEdge ca = new IEdge(nodify.encode(tric), nodify.encode(tria));

      nodeEdgeLut.computeIfAbsent(ab.a, key -> new ArrayList<>()).add(ab);
      nodeEdgeLut.computeIfAbsent(ab.b, key -> new ArrayList<>()).add(ab);

      nodeEdgeLut.computeIfAbsent(bc.a, key -> new ArrayList<>()).add(bc);
      nodeEdgeLut.computeIfAbsent(bc.b, key -> new ArrayList<>()).add(bc);

      nodeEdgeLut.computeIfAbsent(ca.a, key -> new ArrayList<>()).add(ca);
      nodeEdgeLut.computeIfAbsent(ca.b, key -> new ArrayList<>()).add(ca);

      ITri itri = new ITri(ab, bc, ca);

      tris.add(itri);

      edgeTriLut.computeIfAbsent(ab, key -> new ArrayList<>()).add(itri);
      edgeTriLut.computeIfAbsent(bc, key -> new ArrayList<>()).add(itri);
      edgeTriLut.computeIfAbsent(ca, key -> new ArrayList<>()).add(itri);
    }

    String prefix = FilenameUtils.getBaseName(objFile.getName());

    return triOpStack(edgeTriLut, triCount, tris, nodify, nodeEdgeLut, prefix);
  }

  private static File triOpStack(
      Map<IEdge, List<ITri>> edgeTriLut,
      int triCount,
      List<ITri> allTris,
      Nodify3d nodify,
      Map<Integer, List<IEdge>> nodeEdgeLut,
      String dumpPrefix)
      throws IOException {
    for (List<ITri> tris : edgeTriLut.values()) {
      if (tris.size() != 2) {
        throw new IOException(
            "Invalid mesh topology. Expected 2 triangles per edge! Actual: " + tris.size());
      }
    }

    int triCountAfter = triCount;

    Stack<ITri> open = new Stack<>();
    Stack<ITri> closed = new Stack<>();

    closed.push(allTris.get(0));

    while (!closed.isEmpty()) {
      ITri tri = closed.pop();

      if (tri.visited) {
        continue;
      }

      tri.visited = true;

      if (canRemove(tri, edgeTriLut) && !isEdgeCase(tri, edgeTriLut, nodeEdgeLut)) {

        tri.removed = true;

        triCountAfter--;

        // Remove all references.
        edgeTriLut.get(tri.ab).remove(tri);
        edgeTriLut.get(tri.bc).remove(tri);
        edgeTriLut.get(tri.ca).remove(tri);

      } else {
        open.add(tri);
      }

      // Add the neighbours to the check list.
      closed.addAll(edgeTriLut.get(tri.ab));
      closed.addAll(edgeTriLut.get(tri.bc));
      closed.addAll(edgeTriLut.get(tri.ca));
    }

    File target = dumpTriangles(nodify, open, dumpPrefix);

    System.out.println("Strategy: stack");
    System.out.println(
        "Triangles before: " + triCount + ", triangles after: " + triCountAfter + ".");
    double p = (triCountAfter * 100.0) / triCount;
    System.out.println("Removed " + (100.0 - p) + "% of triangles.");

    return target;
  }

  protected static File dumpTriangles(Nodify3d nodify, List<ITri> tris, String prefix)
      throws IOException {

    if (!DUMP) {
      return null;
    }

    File target = new File("dump/" + prefix + "_decimated.tbin");

    target.getParentFile().mkdirs();

    DataOutputStream dos = new DataOutputStream(new FileOutputStream(target));

    dos.writeInt(nodify.size());

    for (int i = 0; i < nodify.size(); i++) {
      Vector3f v = nodify.decode(i);
      dos.writeFloat(v.x);
      dos.writeFloat(v.y);
      dos.writeFloat(v.z);
    }

    for (ITri tri : tris) {
      dos.writeInt(tri._ab.getCommon(tri._bc));
      dos.writeInt(tri._bc.getCommon(tri._ca));
      dos.writeInt(tri._ca.getCommon(tri._ab));
    }

    System.out.println("Num triangles: " + tris.size());

    dos.close();

    return target;
  }

  private static boolean DUMP = true;
  private static boolean CHECK_FOR_INVALID_TRIANGLES = true;

  private static boolean isEdgeCase(
      ITri tri, Map<IEdge, List<ITri>> edgeTriLut, Map<Integer, List<IEdge>> nodeEdgeLut) {
    // An edge case (check the case.obj) is where edges can form a triangle which does not exist in
    // the original model.

    if (!CHECK_FOR_INVALID_TRIANGLES) {
      return false;
    }

    if (canEdgeFormInvalidTriangle(tri.ab, edgeTriLut, nodeEdgeLut)) {
      return true;
    } else if (canEdgeFormInvalidTriangle(tri.bc, edgeTriLut, nodeEdgeLut)) {
      return true;
    } else if (canEdgeFormInvalidTriangle(tri.ca, edgeTriLut, nodeEdgeLut)) {
      return true;
    }

    return false;
  }

  private static boolean canEdgeFormInvalidTriangle(
      IEdge c, Map<IEdge, List<ITri>> lut, Map<Integer, List<IEdge>> nodeEdgeLut) {

    for (IEdge a : nodeEdgeLut.get(c.a)) {

      if (a.equals(c)) {
        continue;
      }

      for (IEdge b : nodeEdgeLut.get(c.b)) {

        if (b.equals(a) || b.equals(c)) {
          continue;
        }

        if (a.touches(b) && a.touches(c) && b.touches(c)) {

          // We have 3 edges that touch which means they might form a triangle. If they form a
          // triangle which does not exist, the edges are invalid.

          // If these edges can form a triangle which does not exist, the edges are invalid.
          ITri test = new ITri(a, b, c);
          IEdge[] edges = {a, b, c};

          boolean found = false;

          for (IEdge e : edges) {
            if (lut.get(e).contains(test)) {
              found = true;
              break;
            }
          }

          if (!found) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean canRemove(ITri tri, Map<IEdge, List<ITri>> lut) {

    // A triangle can safely be removed if the edges reference one other triangle.
    if (lut.get(tri.ab).stream().filter(t -> !t.removed).count() != 2) {
      return false;
    }

    if (lut.get(tri.bc).stream().filter(t -> !t.removed).count() != 2) {
      return false;
    }

    if (lut.get(tri.ca).stream().filter(t -> !t.removed).count() != 2) {
      return false;
    }

    return true;
  }

  @AllArgsConstructor
  private static class Obj3dFace {

    private final int i;
    private final int j;
    private final int k;

    public int getVertexIndex(int x) {
      switch (x) {
        case 0:
          return i;
        case 1:
          return j;
        case 2:
          return k;
      }
      return -1;
    }
  }

  private static class Obj3d {

    private List<Obj3dFace> faces = new ArrayList<ToCoS.Obj3dFace>();

    public static File writeObj(Obj3d src, File file) throws FileNotFoundException, IOException {
      // Write as OBJ, re-save with ASSIMP.
      int numVerts = src.getNumVertices();
      Obj out = Objs.create();

      for (int i = 0; i < numVerts; i++) {
        Vector3f v = src.getVertex(i);

        out.addVertex(v.x, v.y, v.z);
      }

      for (int f = 0; f < src.getNumFaces(); f++) {
        Obj3dFace face = src.getFace(f);

        out.addFace(face.getVertexIndex(0), face.getVertexIndex(1), face.getVertexIndex(2));
      }

      try (FileOutputStream fout = new FileOutputStream(file)) {
        ObjWriter.write(out, fout);
      }

      return file;
    }

    public int getNumFaces() {
      return faces.size();
    }

    private static Map<String, String> extToFormatId = null;

    public static void saveAs(File file, String target) {

      if (extToFormatId == null) {
        extToFormatId = new HashMap<String, String>();

        long c = Assimp.aiGetExportFormatCount();

        for (long i = 0; i < c; i++) {
          AIExportFormatDesc desc = Assimp.aiGetExportFormatDescription(i);
          extToFormatId.put(desc.fileExtensionString().toLowerCase(), desc.idString());
        }
      }

      String formatId = extToFormatId.get(FilenameUtils.getExtension(target).toLowerCase());

      AIScene scene = Assimp.aiImportFile(file.getAbsolutePath(), Assimp.aiProcess_Triangulate);
      Assimp.aiExportScene(scene, formatId, target, Assimp.aiProcess_Triangulate);
    }

    public static Obj3d read(File file) {

      AIScene scene = Assimp.aiImportFile(file.getAbsolutePath(), Assimp.aiProcess_Triangulate);

      AIMesh mesh = AIMesh.create(scene.mMeshes().get(0));

      Obj3d obj = new Obj3d();

      for (int i = 0; i < mesh.mNumVertices(); i++) {
        AIVector3D _a = mesh.mVertices().get(i);

        Vector3f tria = new Vector3f(_a.x(), _a.y(), _a.z());

        obj.addVertex(tria);
      }

      for (int f = 0; f < mesh.mNumFaces(); f++) {
        AIFace face = mesh.mFaces().get(f);

        int a = face.mIndices().get(0);
        int b = face.mIndices().get(1);
        int c = face.mIndices().get(2);

        obj.addFace(a, b, c);
      }

      return obj;
    }

    public int getNumVertices() {
      return n3d.size();
    }

    private Nodify3d n3d = new Nodify3d();
    private Map<Integer, Integer> remap = new HashMap<Integer, Integer>();
    private int n = 0;

    public void addVertex(Vector3f vertex) {
      int pos = n3d.encode(vertex);
      remap.put(n++, pos);
    }

    public void addFace(int a, int b, int c) {
      faces.add(new Obj3dFace(remap.get(a), remap.get(b), remap.get(c)));
    }

    public Vector3f getVertex(int i) {
      return n3d.decode(i);
    }

    public Obj3dFace getFace(int i) {
      return faces.get(i);
    }

    public void addVertex(float x, float y, float z) {
      addVertex(new Vector3f(x, y, z));
    }
  }

  public static void opt2full(File tbinOpt, File recObj) throws IOException, IOException {
    // Restore the optimized triangular mesh.
    Obj3d obj = Obj3d.read(tbinOpt);

    Obj3d rec = new Obj3d();

    for (int i = 0; i < obj.getNumVertices(); i++) {
      Vector3f vertex = obj.getVertex(i);
      rec.addVertex(vertex);
    }

    Nodify3d n = rec.n3d;

    Map<Integer, List<IEdge>> nodeEdgeLut = new HashMap<>();
    Map<IEdge, List<ITri>> edgeTriLut = new HashMap<>();
    List<ITri> tris = new ArrayList<>();

    for (int i = 0; i < obj.getNumFaces(); i++) {
      Obj3dFace face = obj.getFace(i);

      int a = face.getVertexIndex(0);
      int b = face.getVertexIndex(1);
      int c = face.getVertexIndex(2);

      IEdge ab = new IEdge(a, b);
      IEdge bc = new IEdge(b, c);
      IEdge ca = new IEdge(c, a);

      ITri tri = new ITri(ab, bc, ca);

      tris.add(tri);

      rec.addFace(a, b, c);

      edgeTriLut.computeIfAbsent(ab, key -> new ArrayList<>()).add(tri);
      edgeTriLut.computeIfAbsent(bc, key -> new ArrayList<>()).add(tri);
      edgeTriLut.computeIfAbsent(ca, key -> new ArrayList<>()).add(tri);

      nodeEdgeLut.computeIfAbsent(a, key -> new ArrayList<>()).add(ab);
      nodeEdgeLut.computeIfAbsent(b, key -> new ArrayList<>()).add(ab);
      nodeEdgeLut.computeIfAbsent(b, key -> new ArrayList<>()).add(bc);
      nodeEdgeLut.computeIfAbsent(c, key -> new ArrayList<>()).add(bc);
      nodeEdgeLut.computeIfAbsent(c, key -> new ArrayList<>()).add(ca);
      nodeEdgeLut.computeIfAbsent(a, key -> new ArrayList<>()).add(ca);
    }

    for (IEdge c : edgeTriLut.keySet()) {

      if (edgeTriLut.get(c).size() == 2) {
        continue;
      }

      for (IEdge a : nodeEdgeLut.get(c.a)) {

        if (c.equals(a)) {
          continue;
        }

        if (edgeTriLut.get(c).size() == 2) {
          break;
        }

        if (edgeTriLut.get(a).size() == 2) {
          continue;
        }

        for (IEdge b : nodeEdgeLut.get(c.b)) {

          if (c.equals(b) || a.equals(b)) {
            continue;
          }

          if (edgeTriLut.get(c).size() == 2) {
            break;
          }

          if (edgeTriLut.get(a).size() == 2) {
            break;
          }

          if (edgeTriLut.get(b).size() == 2) {
            continue;
          }

          if (a.touches(b) && a.touches(c) && b.touches(c)) {
            ITri tri = new ITri(a, b, c);

            // Ignore existing triangles.
            if (edgeTriLut.get(a).contains(tri)) {
              continue;
            }

            ITri existing = edgeTriLut.get(a).get(0);
            boolean swap = false;

            if (normal(existing, n).dot(normal(tri, n)) < 0) {
              swap = true;
            }

            if (swap) {
              tri.reverse();
            }

            tris.add(tri);

            int fa = tri._ab.getCommon(tri._bc);
            int fb = tri._bc.getCommon(tri._ca);
            int fc = tri._ca.getCommon(tri._ab);

            rec.addFace(fa, fb, fc);

            edgeTriLut.get(a).add(tri);
            edgeTriLut.get(b).add(tri);
            edgeTriLut.get(c).add(tri);
          }
        }
      }
    }

    Obj3d.writeObj(rec, recObj);

    int s = 0;
    for (IEdge e : edgeTriLut.keySet()) {
      if (edgeTriLut.get(e).size() != 2) {
        s++;
      }
    }

    System.out.println("Num. of problematic edges: " + s);
    System.out.println("Num. of triangles: " + tris.size());
    System.out.println();
  }

  private static Vector3f normal(ITri tri, Nodify3d n) {

    int a = tri._ab.getCommon(tri._bc);
    int b = tri._bc.getCommon(tri._ca);
    int c = tri._ca.getCommon(tri._ab);

    Vector3f ab = new Vector3f(n.decode(b)).sub(n.decode(a));
    Vector3f ac = new Vector3f(n.decode(c)).sub(n.decode(a));

    return ab.cross(ac).normalize();
  }

  public static void obj2target(Obj3d obj, File stl) throws IOException {
    File dummy = new File("dummy.obj");
    Obj3d.saveAs(Obj3d.writeObj(obj, dummy), stl.getAbsolutePath());
    dummy.delete();
  }

  private static void tbinToObj(File tbinFile, File objFile) throws IOException {

    DataInputStream in = new DataInputStream(new FileInputStream(tbinFile));
    int numVerts = in.readInt();
    Obj3d obj = new Obj3d();

    for (int i = 0; i < numVerts; i++) {
      float x = in.readFloat();
      float y = in.readFloat();
      float z = in.readFloat();

      obj.addVertex(x, y, z);
    }

    while (true) {
      try {
        int a = in.readInt();
        int b = in.readInt();
        int c = in.readInt();

        obj.addFace(a, b, c);

      } catch (EOFException e) {
        break;
      }
    }

    in.close();

    Obj3d.writeObj(obj, objFile);

    System.out.println("Num faces from triangles: " + obj.getNumFaces());
  }

  private static final String ext = "CoToS";

  private static void printUsage() {
    System.out.println(
        "Compress: c input.[obj|stl|...any assimp supported format]. Example: c sphere.obj (will compress sphere.obj in to sphere.obj.CoToS)");
    System.out.println("Decompress: d input.[obj|stl|...any assimp supported format].CoToS");
  }

  public static void main(String[] args) {

    if (args.length != 2) {
      printUsage();
    } else if (args[0].equals("c")) {
      // Compress.
      try {
        compress(args[1]);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else if (args[0].equals("d")) {
      // Decompress.
      try {
        decompress(args[1]);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      printUsage();
    }
  }

  private static void decompress(String src) throws IOException {
    if (!src.endsWith(ext)) {
      System.out.println("Invalid file to decompress. Must end in 'CoToS'.");
      return;
    }

    File srcFile = new File(src);

    File renamedFile =
        new File(srcFile.getParentFile(), FilenameUtils.getBaseName(srcFile.getName()));

    File renamedFileTmp =
        new File(
            srcFile.getParentFile(),
            FilenameUtils.getBaseName(renamedFile.getName())
                + "_tmp."
                + FilenameUtils.getExtension(renamedFile.getName()));

    FileUtils.copyFile(srcFile, renamedFile);

    opt2full(renamedFile, renamedFileTmp);

    Obj3d obj3d = Obj3d.read(renamedFileTmp);

    renamedFile.delete();

    obj2target(obj3d, renamedFile);

    renamedFileTmp.delete();
  }

  private static void compress(String src) throws IOException {
    // Optimize and dump the mesh.
    File triangleDump = triOptStack(new File(src));

    // Convert optimized mesh to obj.
    File objDecimated =
        new File(
            triangleDump.getParentFile(),
            FilenameUtils.getBaseName(triangleDump.getName()) + ".obj");

    tbinToObj(triangleDump, objDecimated);

    Obj3d decimatedObj3d = Obj3d.read(objDecimated);

    String format = FilenameUtils.getExtension(src);

    // Save decimated to stl and ply.
    File targetDecimated =
        new File(
            new File(src).getParentFile(),
            FilenameUtils.getBaseName(triangleDump.getName()) + "." + format);

    obj2target(decimatedObj3d, targetDecimated);

    File cotos =
        new File(
            new File(src).getParentFile(),
            FilenameUtils.getBaseName(triangleDump.getName()).replace("_decimated", "")
                + "."
                + format
                + "."
                + ext);

    targetDecimated.renameTo(cotos);

    objDecimated.delete();
  }
}
