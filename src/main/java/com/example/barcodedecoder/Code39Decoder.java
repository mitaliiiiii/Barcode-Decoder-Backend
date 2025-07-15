package com.example.barcodedecoder;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Code39Decoder {

    private static final Map<String, Character> CHAR_PATTERN_MAP = Map.ofEntries(
        Map.entry("000110100", '0'), Map.entry("100100001", '1'), Map.entry("001100001", '2'),
        Map.entry("101100000", '3'), Map.entry("000110001", '4'), Map.entry("100110000", '5'),
        Map.entry("001110000", '6'), Map.entry("000100101", '7'), Map.entry("100100100", '8'),
        Map.entry("001100100", '9'), Map.entry("100001001", 'A'), Map.entry("001001001", 'B'),
        Map.entry("101001000", 'C'), Map.entry("000011001", 'D'), Map.entry("100011000", 'E'),
        Map.entry("001011000", 'F'), Map.entry("000001101", 'G'), Map.entry("100001100", 'H'),
        Map.entry("001001100", 'I'), Map.entry("000011100", 'J'), Map.entry("100000011", 'K'),
        Map.entry("001000011", 'L'), Map.entry("101000010", 'M'), Map.entry("000010011", 'N'),
        Map.entry("100010010", 'O'), Map.entry("001010010", 'P'), Map.entry("000000111", 'Q'),
        Map.entry("100000110", 'R'), Map.entry("001000110", 'S'), Map.entry("000010110", 'T'),
        Map.entry("110000001", 'U'), Map.entry("011000001", 'V'), Map.entry("111000000", 'W'),
        Map.entry("010010001", 'X'), Map.entry("110010000", 'Y'), Map.entry("011010000", 'Z'),
        Map.entry("010000101", '-'), Map.entry("110000100", '.'), Map.entry("011000100", ' '),
        Map.entry("010010100", '*'), Map.entry("010101000", '$'), Map.entry("010100010", '/'),
        Map.entry("010001010", '+'), Map.entry("000101010", '%')
    );

    public static String interpretBarcode(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        int[][] gray = new int[height][width];

        // Convert image to grayscale
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                gray[y][x] = (r + g + b) / 3;
            }
        }

        // Rotate if needed
        if (height > width) {
            gray = rotateCounterClockwise(gray);
            int tmp = width;
            width = height;
            height = tmp;
        }

        int min = 255, max = 0;
        for (int[] row : gray) {
            for (int px : row) {
                min = Math.min(min, px);
                max = Math.max(max, px);
            }
        }

        int threshold = (min + max) / 2;
        boolean[][] binary = new boolean[height][width];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                binary[y][x] = gray[y][x] < threshold;

        // Invert if background is black
        int count = 0;
        for (boolean[] row : binary)
            for (boolean b : row)
                if (b) count++;

        if (count > width * height / 2) {
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    binary[y][x] = !binary[y][x];
        }

        // Crop to content
        int x1 = width, y1 = height, x2 = 0, y2 = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (binary[y][x]) {
                    x1 = Math.min(x1, x);
                    y1 = Math.min(y1, y);
                    x2 = Math.max(x2, x);
                    y2 = Math.max(y2, y);
                }

        if (x2 < x1 || y2 < y1) return "";

        boolean[][] region = new boolean[y2 - y1 + 1][x2 - x1 + 1];
        for (int y = y1; y <= y2; y++)
            System.arraycopy(binary[y], x1, region[y - y1], 0, x2 - x1 + 1);

        int row = region.length / 2;
        boolean[] line = region[row];

        // Trim leading 0s
        int start = 0;
        while (start < line.length && !line[start]) start++;
        line = Arrays.copyOfRange(line, start, line.length);

        // Count run lengths
        List<Integer> lens = new ArrayList<>();
        boolean color = line[0];
        int countRun = 1;
        for (int i = 1; i < line.length; i++) {
            if (line[i] == color) {
                countRun++;
            } else {
                lens.add(countRun);
                countRun = 1;
                color = line[i];
            }
        }
        lens.add(countRun);

        List<Double> lensF = new ArrayList<>();
        for (int len : lens) lensF.add((double) len);

        double thin = Collections.min(lensF);
        double thick = Collections.max(lensF);

        for (int iter = 0; iter < 5; iter++) {
            List<Double> thinVals = new ArrayList<>();
            List<Double> thickVals = new ArrayList<>();
            for (double l : lensF) {
                if (Math.abs(l - thin) < Math.abs(l - thick)) thinVals.add(l);
                else thickVals.add(l);
            }

            if (thinVals.isEmpty() || thickVals.isEmpty()) return "";

            double newThin = thinVals.stream().mapToDouble(Double::doubleValue).average().orElse(thin);
            double newThick = thickVals.stream().mapToDouble(Double::doubleValue).average().orElse(thick);
            if (Math.abs(newThin - thin) < 0.5 && Math.abs(newThick - thick) < 0.5) break;
            thin = newThin;
            thick = newThick;
        }

        double limit = (thin + thick) / 2;
        List<Character> bits = new ArrayList<>();
        for (double l : lensF) bits.add(l > limit ? '1' : '0');

        int total = bits.size();
        int usable = ((total + 1) / 10) * 10 - 1;
        if (usable <= 0) return "";

        StringBuilder result = new StringBuilder();
        for (int j = 0; j < (usable + 1) / 10; j++) {
            int i = j * 10;
            StringBuilder pattern = new StringBuilder();
            for (int k = i; k < i + 9; k++) {
                pattern.append(bits.get(k));
            }
            Character ch = CHAR_PATTERN_MAP.get(pattern.toString());
            if (ch == null) return "";
            result.append(ch);
        }

        if (result.length() < 2 || result.charAt(0) != '*' || result.charAt(result.length() - 1) != '*')
            return "";

        return result.substring(1, result.length() - 1);
    }

    private static int[][] rotateCounterClockwise(int[][] mat) {
        int M = mat.length;
        int N = mat[0].length;
        int[][] rotated = new int[N][M];
        for (int r = 0; r < M; r++)
            for (int c = 0; c < N; c++)
                rotated[N - c - 1][r] = mat[r][c];
        return rotated;
    }

    public static void main(String[] args) {
        // Scanner sc = new Scanner(System.in);
        // String path = sc.nextLine().trim();
        // try {
        //     File file = new File(path);
        //     if (file.exists()) {
        //         BufferedImage img = ImageIO.read(file);
        //         String result = interpretBarcode(img);
        //         System.out.println(result);
        //     } else {
        //         System.out.println("");
        //     }
        // } catch (Exception e) {
        //     System.out.println("");
        // }
    }
}


