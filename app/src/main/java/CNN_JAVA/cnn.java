package CNN_JAVA;


import android.content.Context;

import java.io.*;
import java.util.ArrayList;


interface Layer {
    enum LayerType {
        // 网络层的类型：输入层、输出层、卷积层、采样层
        input, output, conv, samp
    }

    void run_forward();

    void print_output();

    Tensor getOutputTensor();
}

class Tensor {
    // 因为每次层与层之间传递的图像数据可能有多个，例如经过卷积层之后可能产生两个图片处理数据，这里使用Arraylist存放
    private ArrayList<double[][]> tensorList = new ArrayList<>();
    private double[][] tensor;

    Tensor() {
    }

    Tensor(double[][] data) {
        this.tensorList.add(data);
    }

    Tensor(ArrayList<double[][]> tensorList) {
        this.tensorList = tensorList;
    }

    public void setTensor(ArrayList<double[][]> tensorList) {
        this.tensorList = tensorList;
    }

    Tensor(int height, int width) {
        this.tensor = new double[height][width];
        this.tensorList.add(this.tensor);
    }

    public ArrayList<double[][]> getTensor() {
        return this.tensorList;
    }

    public void setTensorByPixel(int index, int i, int j, double val) {
        this.getTensor().get(index)[i][j] = val;
    }

    public void printTensor() {
        for (double[][] tensor : tensorList) {
            for (int i = 0; i < tensor.length; i++) {
                for (int j = 0; j < tensor[0].length; j++) {
                    System.out.print(tensor[i][j] + "\t");
                }
                System.out.println();
            }
            System.out.println("********************************");
        }

    }
}

class CONV implements Layer {
    enum CONV_TYPE {same, vaild, full}

    private LayerType Conv;
    private String TYPE;
    private int FilterNum;                  // 卷积层中有几个卷积核
    private int FilterSize;
    private ArrayList<double[][][]> FilterPara;
    private ArrayList<double[]> Bias;      // 每个卷积滤波器设置一个bias
    private Tensor inputTensor;
    private Tensor outputTensor = new Tensor();

    public CONV(Tensor inputTensor, int filternum, int filtersize, String type) {
        this.inputTensor = inputTensor;
        this.FilterNum = filternum;
        this.FilterSize = filtersize;
        this.TYPE = type;
    }

    public void setParameters(ArrayList<double[][][]> para, ArrayList<double[]> bias) {
        this.FilterPara = para;
        this.Bias = bias;
    }


    public void printPara() {
        for (double[][][] p : FilterPara) {
            for (int i = 0; i < p.length; i++) {
                for (int j = 0; j < p[0].length; j++) {
                    for (int k = 0; k < p[0][0].length; k++) {
                        System.out.println(p[i][j][k]);
                    }
                }
            }
        }
    }

    public Tensor getOutputTensor() {
        return this.outputTensor;
    }

    // 更具 type 类型相应改变输入数据类型
    public void SAME() {
        ArrayList<double[][]> input_same = new ArrayList<>();
        int InputSize = this.inputTensor.getTensor().get(0).length;
        int AddSize = this.FilterSize - 1;
        for (double[][] InitInput : this.inputTensor.getTensor()) {

            double[][] newInput = new double[InputSize + AddSize][InputSize + AddSize];
            for (int i = 0; i < newInput.length; i++) {
                for (int j = 0; j < newInput[0].length; j++) {
                    // 如果是不需要扩展的部分，直接将原始数组拿来即可
                    if (i >= AddSize / 2 && j >= AddSize / 2 && i < InputSize + AddSize / 2 && j < InputSize + AddSize / 2) {
                        newInput[i][j] = InitInput[i - AddSize / 2][j - AddSize / 2];
                    } else {
                        // 因为same需要扩展的部分，补0
                        newInput[i][j] = 0;
                    }
                }
            }
            input_same.add(newInput);
        }
        this.inputTensor.setTensor(input_same);
    }

    @Override
    public void run_forward() {
        if (this.TYPE.equals("same")) {
            SAME();
        }
        int TensorNum = this.inputTensor.getTensor().size();
        int FilterNum = this.FilterPara.size();

        double[][][] tensor = new double[TensorNum][][];
        double[][][][] filter = new double[FilterNum][this.FilterPara.get(0)[0][0].length][this.FilterSize][this.FilterSize];
        // 将数据和卷积核从 ArrayList 中提取出来，放入数组
        for (int i = 0; i < TensorNum; i++) {
            tensor[i] = this.inputTensor.getTensor().get(i);
        }
        for (int i = 0; i < FilterNum; i++) {
            for (int j = 0; j < this.FilterSize; j++) {
                for (int k = 0; k < this.FilterSize; k++) {
                    for (int l = 0; l < this.FilterPara.get(0)[0][0].length; l++) {
                        // 将 5*5*1改为1*5*5
                        filter[i][l][j][k] = this.FilterPara.get(i)[j][k][l];
                    }
                }
            }
        }
        // 每个卷积核对输入图像进行卷积操作，得到 filternum * tensorlistnum 个结果

        double[][][] OutPutList = new double[FilterNum][][];
        for (int i = 0; i < FilterNum; i++) {
            double[][][] temp = new double[FilterNum * TensorNum][][];
            int index = 0;
            for (int j = 0; j < TensorNum; j++) {
//                this.outputTensor.getTensor().add(convRes(tensor[j],filter[i][j], this.Bias.get(i)[0]));
                temp[index] = convRes(tensor[j], filter[i][j], this.Bias.get(i)[0]);
                index++;
            }
            double[][] data = new double[temp[0].length][temp[0].length];
            for (int j = 0; j < data.length; j++) {
                for (int k = 0; k < data.length; k++) {
                    for (int l = 0; l < index; l++) {
                        data[j][k] += temp[l][j][k];
                    }
                }
            }
            OutPutList[i] = data;
        }
        // 将所有卷积输出结果add到outputTensor，数量应该是卷积核的个数
        for (double[][] data :
                OutPutList) {
            outputTensor.getTensor().add(data);
        }

    }

    // 计算单个输入和单个卷积核的卷积结果
    public double[][] convRes(double[][] InputPic, double[][] filter, double bias) {
        int InputWidth = InputPic.length;
        int InputHeight = InputPic[0].length;
        int OutputWidth = InputWidth - filter.length + 1;
        int OutputHeight = InputHeight - filter.length + 1;
        // 设定输出向量的临时值
        double[][] output_temp = new double[OutputHeight][OutputWidth];

        // filter在输入tensor上滑动
        for (int j = 0; j < OutputWidth; j++) {
            for (int i = 0; i < OutputHeight; i++) {
                // 每次滑动计算生成的单个神经元的值
                double temp = 0.0;
                for (int k = 0; k < this.FilterSize; k++) {
                    for (int l = 0; l < this.FilterSize; l++) {
                        temp += InputPic[i + k][j + l] * filter[k][l];
                    }
                }
                // 记录单次滑动卷积层生成的值
                output_temp[i][j] = temp + bias;
            }
        }
        return output_temp;
    }

    @Override
    public void print_output() {
        System.out.println("**************  After the Conv layer  ************************");
        outputTensor.printTensor();
    }
}

class POOL implements Layer {
    enum POOL_TYPE {max, average}

    private POOL_TYPE type;
    private int PoolSize;
    private Tensor InputTensor;
    private Tensor OutputTensor = new Tensor();

    public POOL() {
    }

    public POOL(Tensor input, POOL_TYPE type, int poolSize) {
        this.InputTensor = input;
        this.type = type;
        this.PoolSize = poolSize;
    }

    public void padding() {
        // 设置新的临时tensor，用于保存padding过后的数据
        Tensor temp_tensor = new Tensor();
        int size = this.InputTensor.getTensor().get(0)[0].length;
        for (int i = 0; i < this.InputTensor.getTensor().size(); i++) {
            temp_tensor = new Tensor(this.InputTensor.getTensor());

            // 如果输入tensor的shape不是偶数，则进行增加一行，一列的padding操作
            if (size / 2 != 0) {

                for (int j = 0; j < size; j++) {
                    for (int k = 0; k < size; k++) {
                        temp_tensor.setTensorByPixel(i, j, k, this.InputTensor.getTensor().get(i)[j][k]);
                    }
                }
                // padding新加的一行和一列
                for (int l = 0; l < size + 1; l++) {
                    temp_tensor.setTensorByPixel(i, l, size, 0);
                    temp_tensor.setTensorByPixel(i, size, l, 0);
                }
                this.InputTensor = temp_tensor;
            }
        }
    }

    @Override
    public void run_forward() {
        // 在前向传播之前先进行padding
//        padding();
        int inputsize = InputTensor.getTensor().get(0).length;
        int outputsize = inputsize / this.PoolSize;
        ArrayList<double[][]> output_ArrayList = new ArrayList<>();

        for (double[][] data :
                this.InputTensor.getTensor()) {
            double[][] output_temp = new double[outputsize][outputsize];
            int x = 0;
            int y = 0;
            for (int i = 0; i < inputsize; i += this.PoolSize) {
                // 如果这次循环结束时越界，则舍弃
                if (i + this.PoolSize - 1 >= inputsize)
                    break;
                for (int j = 0; j < inputsize; j += this.PoolSize) {
                    // 如果这次循环结束时越界，则舍弃
                    if (j + this.PoolSize - 1 >= inputsize)
                        break;
                    double max = findMax(data, i, j);
                    output_temp[x][y] = max;
                    // 一次输入到outputTensor中，以x为索引
                    x++;
                }
                // 开始新一的行，x置0，逐行扫描
                x = 0;
                y++;
            }
            output_ArrayList.add(output_temp);
        }
        OutputTensor.setTensor(output_ArrayList);
    }

    @Override
    public void print_output() {
        System.out.println("***********************  After the Pooling layer  **********************");
        OutputTensor.printTensor();
    }

    public double findMax(double[][] data, int x, int y) {
        double temp_max = -Double.MAX_VALUE;
        for (int i = x; i < x + this.PoolSize; i++) {
            for (int j = y; j < y + this.PoolSize; j++) {
                if (data[i][j] > temp_max) {
                    temp_max = data[i][j];
                }
            }
        }
        return temp_max;
    }

    public Tensor getOutputTensor() {
        return this.OutputTensor;
    }
}

class FLATTEN implements Layer {
    private Tensor InputTensor;
    private Tensor OutputTensor;
    private int InputNum;
    private int InputSize;
    private int OutputSize;
    private int index = 0;

    public FLATTEN() {
    }

    public FLATTEN(Tensor input) {
        this.InputTensor = input;
    }

    public Tensor getOutputTensor() {
        return this.OutputTensor;
    }

    @Override
    public void run_forward() {
        this.InputNum = InputTensor.getTensor().size();
        this.InputSize = InputTensor.getTensor().get(0).length;
        this.OutputSize = this.InputNum * this.InputSize * this.InputSize;
        OutputTensor = new Tensor(this.OutputSize, 1);

        for (double[][] data :
                this.InputTensor.getTensor()) {
            for (int i = 0; i < this.InputSize; i++) {
                for (int j = 0; j < this.InputSize; j++) {
                    OutputTensor.setTensorByPixel(0, index, 0, data[i][j]);
                    index++;
                }
            }
        }
    }

    @Override
    public void print_output() {
        System.out.println("***************** After the Flatten layer *** the length of the output is: " + index);
        OutputTensor.printTensor();
    }
}

class DENSE implements Layer {
    private Tensor InputTensor;
    private Tensor OutputTensor;
    private double[][] DensePara;
    private double[] DenseBias;

    public DENSE() {
    }

    public DENSE(Tensor input, int outsize) {
        this.InputTensor = input;
        this.OutputTensor = new Tensor(outsize, 1);
    }

    public void setDensePara(double[][] densePara, double[] denseBias) {
        this.DensePara = densePara;
        this.DenseBias = denseBias;
    }

    public Tensor getOutputTensor() {
        return this.OutputTensor;
    }

    @Override
    public void run_forward() {
        ArrayList<double[][]> OutputList = new ArrayList<>();
        double[][] InputData = this.InputTensor.getTensor().get(0);
        double[][] OutputData = this.OutputTensor.getTensor().get(0);
        for (int i = 0; i < OutputData.length; i++) {
            double temp = 0.0;
            for (int j = 0; j < InputData.length; j++) {
                temp += InputData[j][0] * this.DensePara[j][i];
            }
            OutputData[i][0] = temp + this.DenseBias[i];
        }
        OutputList.add(OutputData);
        this.OutputTensor.setTensor(OutputList);
    }

    @Override
    public void print_output() {
        System.out.println("****** After the Dense layer, the output size is :" + OutputTensor.getTensor().get(0).length);
        this.OutputTensor.printTensor();
    }
}


public class cnn {
    public static void main(String[] args) {

        String packageName = cnn.class.getPackage().getName();
        System.out.println(packageName);

    }

    public static String run_cnn(Context context, double[][] imageData){

        String ret = "@ 左、中、右、转弯的概率分别为：\n";
        //**************** 读取第一层卷积参数开始 *************

        double[][][] conv2d_1_1 = new double[5][5][1];
        double[][][] conv2d_1_2 = new double[5][5][1];
        double[] conv2d_1_1_bias = new double[1];
        double[] conv2d_1_2_bias = new double[1];
        try {
            InputStream inputstream_conv = context.getResources().getAssets().open("conv2d_1.txt");
            InputStream inputstream_bias = context.getResources().getAssets().open("conv2d_1_bias.txt");
            ReadPara_conv_1(inputstream_conv, conv2d_1_1, conv2d_1_2);
            ReadPara_conv_1_bias(inputstream_bias, conv2d_1_1_bias, conv2d_1_2_bias);
        }catch (IOException e){
            e.printStackTrace();
        }


        ArrayList<double[][][]> conv1_para = new ArrayList<>();
        ArrayList<double[]> conv1_bias = new ArrayList<>();
        conv1_para.add(conv2d_1_1);
        conv1_para.add(conv2d_1_2);
        conv1_bias.add(conv2d_1_1_bias);
        conv1_bias.add(conv2d_1_2_bias);
        //**************** 读取第一层卷积参数结束 *************


        //**************** 读取第二层卷积参数开始 *************

        double[][][] conv2d_2_1 = new double[21][21][2];
        double[][][] conv2d_2_2 = new double[21][21][2];
        double[] conv2d_2_1_bias = new double[1];
        double[] conv2d_2_2_bias = new double[1];
        try {
            InputStream inputstream_conv = context.getResources().getAssets().open("conv2d_2.txt");
            InputStream inputstream_bias = context.getResources().getAssets().open("conv2d_2_bias.txt");
            ReadPara_conv_2(inputstream_conv, conv2d_2_1, conv2d_2_2);
            ReadPara_conv_1_bias(inputstream_bias, conv2d_2_1_bias, conv2d_2_2_bias);
        }catch (IOException e){
            e.printStackTrace();
        }


        ArrayList<double[][][]> conv2_para = new ArrayList<>();
        ArrayList<double[]> conv2_bias = new ArrayList<>();
        conv2_para.add(conv2d_2_1);
        conv2_para.add(conv2d_2_2);
        conv2_bias.add(conv2d_2_1_bias);
        conv2_bias.add(conv2d_2_2_bias);
        //**************** 读取第二层卷积参数结束 *************

        //**************** 读取 dense_1 参数开始 *************

        double[][] dense_1 = new double[968][128];
        double[] dense_1_bias = new double[128];

        try {
            InputStream inputstream_conv = context.getResources().getAssets().open("dense_1.txt");
            InputStream inputstream_bias = context.getResources().getAssets().open("dense_1_bias.txt");
            ReadPara_dense(inputstream_conv, dense_1);
            ReadPara_dense_bias(inputstream_bias, dense_1_bias);
        }catch (IOException e){
            e.printStackTrace();
        }

        //**************** 读取 dense_1 参数结束 *************

        //**************** 读取 dense_2 参数开始 *************

        double[][] dense_2 = new double[128][4];
        double[] dense_2_bias = new double[4];

        try {
            InputStream inputstream_conv = context.getResources().getAssets().open("dense_2.txt");
            InputStream inputstream_bias = context.getResources().getAssets().open("dense_2_bias.txt");
            ReadPara_dense(inputstream_conv, dense_2);
            ReadPara_dense_bias(inputstream_bias, dense_2_bias);
        }catch (IOException e){
            e.printStackTrace();
        }
        //**************** 读取 dense_2 参数结束 *************


        Tensor inputImage = new Tensor(imageData);

        CONV conv1 = new CONV(inputImage, 2, 5, "same");
        conv1.setParameters(conv1_para, conv1_bias);
        conv1.run_forward();
//        conv1.print_output();
        Tensor conv1_out = util.Activation(conv1.getOutputTensor(), "tanh");

        CONV conv2 = new CONV(conv1_out, 2, 21, "full");
        conv2.setParameters(conv2_para, conv2_bias);
        conv2.run_forward();
//        conv2.print_output();
        Tensor conv2_out = util.Activation(conv2.getOutputTensor(), "tanh");


        POOL pool1 = new POOL(conv2_out, POOL.POOL_TYPE.max, 2);
        pool1.run_forward();
//        pool1.print_output();

        FLATTEN flatten1 = new FLATTEN(pool1.getOutputTensor());
        flatten1.run_forward();
//        flatten1.print_output();

        DENSE dense1 = new DENSE(flatten1.getOutputTensor(), 128);
        dense1.setDensePara(dense_1, dense_1_bias);
        dense1.run_forward();
        dense1.print_output();
        Tensor dense1_out = util.Activation(dense1.getOutputTensor(), "tanh");

        DENSE dense2 = new DENSE(dense1.getOutputTensor(), 4);
        dense2.setDensePara(dense_2, dense_2_bias);
        dense2.run_forward();
//        dense2.print_output();

        double[] result = util.softmax(util.T(dense2.getOutputTensor().getTensor().get(0)));
        for (double res : result) {
            System.out.println(res);
            ret = ret + res + '\n';
        }
        return ret;
    }


    public static void ReadPara_conv_1(InputStream parafile, double[][][] conv2d_1_1, double[][][] conv2d_1_2) {

        ArrayList<String> temp = util.ReadTxt(parafile);
        System.out.println("**************" + temp.size());

        ArrayList<Double> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        for (String line : temp) {
            // 如果为空，则跳出，发生在文件最后
            if (line == null) {
                break;
            }
            // 如果有空行，则跳过
            if ("".equals(line)) {
                continue;
            }
            // 去掉无用字符
            line = line.replace("[", "");
            line = line.replace("]", "");
            line = line.replace('\n', ' ');
//            line = line.replace(" ", "");
            // 讲字符字符串按照空格进行划分
            String[] splited = line.split("\\s+");

            try {
                for (String s : splited) {
                    if (s.equals('\n')) {
                        System.out.println("@@@");
                    } else if (s.equals("")) {            // 如果有空格，跳过
                        continue;
                    }
                    result.add(Double.parseDouble(s));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println(result.size());
        for (int k = 0; k < result.size() / 2; k++) {
            conv2d_1_1[k / 5][k % 5][0] = result.get(k);
            conv2d_1_2[k / 5][k % 5][0] = result.get(k + 25);
        }
    }

    public static void ReadPara_conv_1_bias(InputStream parafile, double[] conv2d_1_1_bias, double[] conv2d_1_2_bias) {

//        ArrayList<String> temp = util.ReadTxt("E:\\Java project\\CNN\\src\\conv2d_1_bias.txt");

        ArrayList<String> temp = util.ReadTxt(parafile);
        System.out.println("**************" + temp.size());

        ArrayList<Double> result = new ArrayList<>();
        for (String line : temp) {
            // 如果为空，则跳出，发生在文件最后
            if (line == null) {
                break;
            }
            // 如果有空行，则跳过
            if ("".equals(line)) {
                continue;
            }
            // 去掉无用字符
            line = line.replace("[", "");
            line = line.replace("]", "");
            line = line.replace('\n', ' ');
//            line = line.replace(" ", "");
            // 讲字符字符串按照空格进行划分
            String[] splited = line.split("\\s+");

            try {
                for (String s : splited) {
                    if (s.equals('\n')) {
                        System.out.println("@@@");
                    } else if (s.equals("")) {            // 如果以后空格，跳过
                        continue;
                    }
                    result.add(Double.parseDouble(s));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println(result.size());

        conv2d_1_1_bias[0] = result.get(0);
        conv2d_1_2_bias[0] = result.get(1);

    }

    public static void ReadPara_conv_2(InputStream parafile, double[][][] conv2d_2_1, double[][][] conv2d_2_2) {

//        ArrayList<String> temp = util.ReadTxt("E:\\Java project\\CNN\\src\\conv2d_1.txt");
        ArrayList<String> temp = util.ReadTxt(parafile);
        System.out.println("the size befor strip: " + temp.size());

        ArrayList<Double> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        for (String line : temp) {
            // 如果为空，则跳出，发生在文件最后
            if (line == null) {
                break;
            }
            // 如果有空行，则跳过
            if ("".equals(line)) {
                continue;
            }
            // 去掉无用字符
            line = line.replace("[", "");
            line = line.replace("]", "");
            line = line.replace('\n', ' ');
//            line = line.replace(" ", "");
            // 讲字符字符串按照空格进行划分
            String[] splited = line.split("\\s+");

            try {
                for (String s : splited) {
                    if (s.equals('\n')) {
                        System.out.println("@@@");
                    } else if (s.equals("")) {            // 如果有空格，跳过
                        continue;
                    }
                    result.add(Double.parseDouble(s));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("the size after strip: " + result.size());

        for (int k = 0; k < result.size() / 2; k += 2) {
            // 下面的index需要仔细推导
            conv2d_2_1[k / 42][k % 42 / 2][0] = result.get(k);
            conv2d_2_2[k / 42][k % 42 / 2][0] = result.get(k + 882);
            conv2d_2_1[k / 42][k % 42 / 2][1] = result.get(k + 1);
            conv2d_2_2[k / 42][k % 42 / 2][1] = result.get(k + 882 + 1);

        }
    }

    public static void ReadPara_dense(InputStream parafile, double[][] dense_1) {

//        ArrayList<String> temp = util.ReadTxt("E:\\Java project\\CNN\\src\\conv2d_1.txt");
        ArrayList<String> temp = util.ReadTxt(parafile);
        System.out.println("the size befor strip: " + temp.size());

        ArrayList<Double> result = new ArrayList<>();
        for (String line : temp) {
            // 如果为空，则跳出，发生在文件最后
            if (line == null) {
                break;
            }
            // 如果有空行，则跳过
            if ("".equals(line)) {
                continue;
            }
            // 去掉无用字符
            line = line.replace("[", "");
            line = line.replace("]", "");
            line = line.replace('\n', ' ');
//            line = line.replace(" ", "");
            // 讲字符字符串按照空格进行划分
            String[] splited = line.split("\\s+");

            try {
                for (String s : splited) {
                    if (s.equals('\n')) {
                        System.out.println("@@@");
                    } else if (s.equals("")) {            // 如果有空格，跳过
                        continue;
                    }
                    result.add(Double.parseDouble(s));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("the size after strip: " + result.size());
        int index = 0;
        for (int i = 0; i < dense_1.length; i++) {
            for (int j = 0; j < dense_1[0].length; j++) {
                dense_1[i][j] = result.get(index);
                index++;
            }
        }
    }

    public static void ReadPara_dense_bias(InputStream parafile, double[] dense_bias) {

        ArrayList<String> temp = util.ReadTxt(parafile);
        System.out.println("**************" + temp.size());

        ArrayList<Double> result = new ArrayList<>();
        for (String line : temp) {
            // 如果为空，则跳出，发生在文件最后
            if (line == null) {
                break;
            }
            // 如果有空行，则跳过
            if ("".equals(line)) {
                continue;
            }
            // 去掉无用字符
            line = line.replace("[", "");
            line = line.replace("]", "");
            line = line.replace('\n', ' ');
            // 讲字符字符串按照空格进行划分
            String[] splited = line.split("\\s+");

            try {
                for (String s : splited) {
                    if (s.equals('\n')) {
                        System.out.println("@@@");
                    } else if (s.equals("")) {            // 如果以后空格，跳过
                        continue;
                    }
                    result.add(Double.parseDouble(s));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println(result.size());
        int index = 0;
        for (int i = 0; i < dense_bias.length; i++) {
            dense_bias[i] = result.get(index);
            index++;
        }
    }

    public static void ReadPic(String picpath) {
        try {
            FileInputStream fis = new FileInputStream(new File(picpath));
            FileOutputStream fos = new FileOutputStream(new File("E:\\Java project\\CNN\\src\\test.jpg"));
            byte[] read = new byte[1024];
            int len = 0;
            while ((len = fis.read(read)) != -1) {
                fos.write(read, 0, len);
            }
            for (byte d :
                    read) {
                System.out.println(d);
            }
            System.out.println(read.length);

            fis.close();
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            System.out.println("PICTURE is not found! ");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
