//package com.mros;

import utils.bytes.BytesUtil;
import utils.hash.MurmurHash;

import static java.lang.Math.*;

import java.util.BitSet;

public class Mros {
    private final int layerCnt;

    private final int layerSize;

    private int seed = 0;

    private final double beta;

    //Mros的计数范围
    private int countRange;

    //映射到Mros结构中的元素个数
    private int itemNum;

    //底部的位数组
    private BitSet[] bitmaps;

    /**
     * @param layerCnt  层数
     * @param layerSize 每一层的比特数
     * @param beta      the upper bound of 1-bits ratio
     * @param seed      哈希种子
     */
    public Mros(int layerCnt, int layerSize, double beta, int seed) {
        this.layerCnt = layerCnt;
        this.layerSize = layerSize;
        this.countRange = (1 << layerCnt) * layerSize;
        this.itemNum = 0;
        this.bitmaps = new BitSet[layerCnt];
        this.beta = beta;
        this.seed = seed;
        for (int i = 0; i < bitmaps.length; i++) {
            if (i == bitmaps.length - 1) bitmaps[i] = new BitSet(2 * layerSize);
            else bitmaps[i] = new BitSet(layerSize);
        }
    }

    /**
     * 添加元素key
     */
    public void add(int key) {
        update(key);
        itemNum++;
    }

    /**
     * 删除元素key
     */
    public void remove(int key) {
        update(key);
        itemNum--;
    }
    /**
     * 集合元素真实值
     */
    public int getItemNum() {
        return itemNum;
    }

    /**
     * 集合元素数量的估计值
     */
    public double sizeEstimate() {
        int b;
        for (b = layerCnt - 1; b >= 0; b--) {
            if ((b - 1) > 0 && oneNum(b - 1) > layerSize * beta) break;
        }

        if (b == -1) b = 0;
        if (b == layerCnt - 1 && oneNum(b) > 2 * layerSize * beta)
            System.out.println("Warning: estimation may be inaccurate");

        double sum = 0;

        for (int i = b; i < layerCnt - 1; i++) {
            sum += -(1.0 * layerSize / 2) * log(1 - 2.0 * oneNum(i) / layerSize);
        }

        sum += -(layerSize * 1.0) * log(1 - oneNum(layerCnt - 1) / layerSize);

        return floor(sum);
    }

    /**
     * @return 两个Mros summary Jaccard similarity的估计值
     */
    public static double JaccardEstimate(Mros o1, Mros o2) {
        double difference = MergeByXor(o1, o2).sizeEstimate();
        double tmp = (o1.itemNum * 1.0 + o2.itemNum * 1.0 - difference) / (o1.itemNum * 1.0 + o2.itemNum * 1.0 + difference);
        return tmp >= 0 ? tmp : 0;
    }

    /**
     * @return 两个集合intersection size估计值
     */
    public static double intersectionSizeEstimate(Mros o1, Mros o2) {
//        if (o1.layerSize != o2.layerSize || o1.layerCnt != o2.layerCnt) {
//            System.err.println("Mros o1和Mros o2大小不同");
//            System.exit(-1);
//        }
//        Mros merge = new Mros(o1.layerCnt, o1.layerSize, o1.beta, o1.seed);
//        for (int i = 0; i < merge.bitmaps.length; i++) {
//            for (int j = 0; j < merge.bitmaps[i].size(); j++) {
//                merge.bitmaps[i].set(j, o1.bitmaps[i].get(j) & o2.bitmaps[i].get(j));
//            }
//        }
        Mros merge = MergeByXor(o1, o2);
        double symmetric_difference = merge.sizeEstimate();
        double tmp = (o1.itemNum * 1.0 + o2.itemNum * 1.0 - symmetric_difference) / 2;
        return tmp >= 0 ? tmp : 0;
    }

    /**
     * @return Mros o1 ^ Mros o2
     */
    private static Mros MergeByXor(Mros o1, Mros o2) {
        if (o1.layerSize != o2.layerSize || o1.layerCnt != o2.layerCnt) {
            System.err.println("Mros o1和Mros o2大小不同");
            System.exit(-1);
        }
        Mros merge = new Mros(o1.layerCnt, o1.layerSize, o1.beta, o1.seed);
        for (int i = 0; i < merge.bitmaps.length; i++) {
            for (int j = 0; j < merge.bitmaps[i].size(); j++) {
                merge.bitmaps[i].set(j, o1.bitmaps[i].get(j) ^ o2.bitmaps[i].get(j));
            }
        }
        return merge;
    }

    private double oneNum(int b) {
        return bitmaps[b].cardinality() * 1.0;
    }

    private void update(int key) {
        int tmp = MurmurHash.hash32(BytesUtil.int2bytes(key), 4, seed);
        int hashVal = (int) ((tmp & 0xffffffffL) % countRange);

        int q = min(
                layerCnt,
                1 + (int) floor(log(countRange * 1.0 / (countRange - hashVal)) / log(2))
        );

        int bitIndex = (int) (layerSize * (q + 1 - pow(2, q)) + floor(hashVal * 1.0 / pow(2, layerCnt - q)));
        int layerIndex = q - 1;

        bitmaps[layerIndex].flip(bitIndex - layerSize * (q - 1));
    }
}
