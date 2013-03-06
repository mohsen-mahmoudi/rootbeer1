package rootbeer.examples.gtc2013;

import edu.syr.pcpratts.rootbeer.runtime.Kernel;
import edu.syr.pcpratts.rootbeer.runtime.RootbeerGpu;
import edu.syr.pcpratts.rootbeer.runtimegpu.GpuException;

import java.util.List;
import java.util.ArrayList;

public class MatrixKernel implements Kernel {

  private float[] m_a;
  private float[] m_b;
  private float[] m_c;
  private int m_blockSize;
  private int m_gridSize;
  private int m_blockIters;
  public CalcList m_calcList;

  public MatrixKernel(float[] a, float[] b, float[] c, int block_size, int grid_size,
    int block_iters){
    m_a = a;
    m_b = b;
    m_c = c;
    m_blockSize = block_size;
    m_gridSize = grid_size;
    m_blockIters = block_iters;
    m_calcList = new CalcList();
  }

  public void gpuMethod(){

    int block_size = m_blockSize;
    int grid_size = m_gridSize;
    int block_iters = m_blockIters;

    int block_idxx = RootbeerGpu.getBlockIdxx();
    int thread_idxx = RootbeerGpu.getThreadIdxx();

    int thread_row = thread_idxx / 32;
    int thread_col = thread_idxx % 32;

    float[] a = m_a;
    float[] b = m_b;
    float[] c = m_c;

    int sub_matrix_size = block_size / 32;
    sub_matrix_size *= sub_matrix_size;

    int m_size = block_size / 32;

    for(int block_iter = 0; block_iter < block_iters; ++block_iter){ 
      for(int sub_matrix = 0; sub_matrix < sub_matrix_size; ++sub_matrix){
        float sum = 0;
        int sub_matrix_row = sub_matrix / m_size;
        int sub_matrix_col = sub_matrix % m_size;

        int dest_row = (32 * sub_matrix_row) + thread_row;
        int dest_col = (32 * sub_matrix_col) + thread_col;

        int dest_index = (block_iter * block_size * block_size * grid_size) + (block_idxx * block_size * block_size) + dest_row * block_size + dest_col;   
  
        for(int m = 0; m < m_size; ++m){
          int a_src_row = (sub_matrix_row * 32) + thread_row;
          int a_src_col = (m * 32) + thread_col;
          int a_src = (a_src_row * block_size) + a_src_col;

          int b_src_row = (m * 32) + thread_row;
          int b_src_col = (sub_matrix_col * 32) + thread_col;
          int b_src = (b_src_row * block_size) + b_src_col;

          float a_value = a[a_src];
          float b_value = b[b_src];

          RootbeerGpu.setSharedFloat(thread_idxx * 4, a_value);
          RootbeerGpu.setSharedFloat((1024 + thread_idxx) * 4, b_value);

          RootbeerGpu.synchthreads();

          for(int k = 0; k < 32; ++k){
            a_value = RootbeerGpu.getSharedFloat((thread_row * 32 + k) * 4);
            b_value = RootbeerGpu.getSharedFloat((1024 + k * 32 + thread_col) * 4);
            sum += a_value * b_value;

            if(dest_index == 0){
              Calculation calc = new Calculation();          
              calc.sub_matrix_row = sub_matrix_row;
              calc.sub_matrix_col = sub_matrix_col;
              calc.sub_matrix = sub_matrix;
              calc.m_size = m_size;
              calc.thread_row = thread_row;
              calc.thread_col = thread_col;
              calc.dest_row = dest_row;
              calc.dest_col = dest_col;
              calc.block_size = block_size;
              calc.dest_index = dest_index;
              calc.m = m;
              calc.k = k;
              calc.a_src_row = a_src_row;
              calc.a_src_col = a_src_col;
              calc.b_src_row = b_src_row;
              calc.b_src_col = b_src_col;
              calc.a_value = a_value;
              calc.b_value = b_value;
              m_calcList.add(calc);
            }
          }
          RootbeerGpu.synchthreads();
        }
        c[dest_index] += sum;
      }
    }
  }
}
