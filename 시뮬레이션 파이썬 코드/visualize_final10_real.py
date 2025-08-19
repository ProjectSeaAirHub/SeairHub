# visualize_final10_real.py (오류 수정된 최종 버전)

# ==============================================================================
# 셀 1: 모든 라이브러리 임포트 및 기본 설정
# ==============================================================================
import pandas as pd
import numpy as np
import pickle
import os
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib import font_manager, rc
from matplotlib.lines import Line2D
import warnings
import itertools

# 경고 메시지 무시 및 디스플레이 설정
warnings.filterwarnings('ignore')
pd.options.display.float_format = '{:,.2f}'.format

# --- 기본 설정 (final10_real.pkl 시뮬레이션과 동일하게 설정) ---
PKL_FILENAME = "final10_real.pkl"
SAVE_DIRECTORY = "final10_real_graphs"
# 아래 두 값은 시뮬레이션 실행 시 사용했던 값과 반드시 동일해야 합니다.
OPERATIONAL_CAPACITY = 52.0
CBM_DISTRIBUTION_PARAMS = {'a': 1, 'b': 15}
# 화물 생성 로직: max(3, ... * 30)
AVG_CBM_CALCULATION_RANGE = 30
AVG_CBM_MINIMUM = 3

# ==============================================================================
# 셀 2: 함수 정의 (데이터 로딩, 폰트 설정, 시각화)
# ==============================================================================
def load_data(filename):
    """지정된 피클 파일을 안전하게 로드합니다."""
    if not os.path.exists(filename):
        print(f"[오류] 파일을 찾을 수 없습니다: {filename}")
        return None
    print(f"'{filename}' 파일 로딩 중...")
    try:
        with open(filename, 'rb') as file:
            df = pickle.load(file)
        print("파일 로딩 완료!")
        # 시나리오 이름 변경 (코드 -> 설명)
        scenario_map = {
            'Primitive_Market': '원시 시장 (Primitive)',
            'B2C_Open_Market': 'B2C 공개 시장',
            'B2B_Enabled_Market': 'B2B 활성화 시장'
        }
        df['시나리오'] = df['시나리오'].map(scenario_map)
        return df
    except Exception as e:
        print(f"[오류] 파일 로딩 중 에러 발생: {e}")
        return None

def setup_korean_font():
    """OS에 맞는 한글 폰트를 설정합니다."""
    print("\n한글 폰트 설정 중...")
    try:
        font_path = "c:/Windows/Fonts/malgun.ttf"
        rc('font', family=font_manager.FontProperties(fname=font_path).get_name())
    except FileNotFoundError:
        try:
            font_path = "/System/Library/Fonts/Supplemental/AppleGothic.ttf"
            rc('font', family=font_manager.FontProperties(fname=font_path).get_name())
        except FileNotFoundError:
            print("[경고] 한글 폰트를 찾지 못했습니다. 영문으로 표시될 수 있습니다.")
    plt.rcParams['axes.unicode_minus'] = False

def plot_kpi(df, equilibrium_points, kpi_column, title, y_label, filename):
    """핵심 지표를 시각화하는 범용 함수"""
    print(f"\n[시각화] '{title}' 그래프 생성 중...")
    is_b2b = kpi_column == 'B2B 거래량'
    
    if is_b2b:
        df_plot = df[df['시나리오'] == 'B2B 활성화 시장']
    else:
        df_plot = df

    palette = None if is_b2b else {'원시 시장 (Primitive)':'#2a9d8f', 'B2C 공개 시장':'#e9c46a', 'B2B 활성화 시장':'#e76f51'}
    
    g = sns.FacetGrid(df_plot, col="포워더 수", row="컨테이너 비용", hue='시나리오' if not is_b2b else None,
                      height=4, aspect=1.2, margin_titles=True, sharey=False, palette=palette,
                      hue_order=['원시 시장 (Primitive)', 'B2C 공개 시장', 'B2B 활성화 시장'])
    
    if is_b2b:
        g.map(sns.lineplot, "요청 건수", kpi_column, lw=3, color='#e76f51', marker='o', ms=5)
    else:
        g.map(sns.lineplot, "요청 건수", kpi_column, marker='o', lw=2, ms=4)
        
    for ax, (col_val, row_val) in zip(g.axes.flat, itertools.product(g.col_names, g.row_names)):
        f_count = col_val
        ax.axvline(x=equilibrium_points.get(f_count, 0), color='red', linestyle=':', linewidth=1.5)

    if not is_b2b:
        handles = [Line2D([0], [0], color=palette[l], marker='o', linestyle='-') for l in g.hue_names]
        labels = g.hue_names
        handles.append(Line2D([0], [0], color='red', linestyle=':', lw=1.5, label='수요-공급 균형점'))
        labels.append('수요-공급 균형점')
        g.add_legend(handles=handles, labels=labels, title="범례")

    g.fig.suptitle(title, fontsize=20, y=1.03)
    g.set_axis_labels("라운드당 평균 요청 건수", y_label)
    g.set_titles(row_template="컨테이너 비용=${row_name}", col_template="포워더 수={col_name}명")
    if '이익' in kpi_column:
        g.map(plt.axhline, y=0, color='grey', linestyle='--')
    g.fig.tight_layout(rect=[0, 0, 0.88, 0.95])
    plt.savefig(os.path.join(SAVE_DIRECTORY, filename), dpi=300)
    plt.close()

def plot_value_add_analysis(df, equilibrium_points, column, title, y_label, filename, palette):
    """플랫폼 도입으로 인한 가치 변화를 시각화하는 함수"""
    print(f"\n[시각화] '{title}' 그래프 생성 중...")
    df_pivot = df.pivot_table(index=['컨테이너 비용','요청 건수','포워더 수'], columns='시나리오', values=column)
    
    df_pivot['B2C 시장 도입 효과'] = df_pivot.get('B2C 공개 시장', 0) - df_pivot.get('원시 시장 (Primitive)', 0)
    df_pivot['B2B 시장 도입 효과'] = df_pivot.get('B2B 활성화 시장', 0) - df_pivot.get('원시 시장 (Primitive)', 0)
    
    df_melted = df_pivot[['B2C 시장 도입 효과','B2B 시장 도입 효과']].reset_index().melt(
        id_vars=['컨테이너 비용','요청 건수','포워더 수'], var_name='개선 효과', value_name='가치 변화 ($)')
        
    g = sns.FacetGrid(df_melted, col="포워더 수", row="컨테이너 비용", hue="개선 효과",
                      height=4, aspect=1.2, margin_titles=True, sharey=False, palette=palette)
    g.map(sns.lineplot, "요청 건수", "가치 변화 ($)", marker='.', lw=2.5)

    for ax, (col_val, row_val) in zip(g.axes.flat, itertools.product(g.col_names, g.row_names)):
        f_count = col_val
        ax.axvline(x=equilibrium_points.get(f_count, 0), color='red', linestyle=':', linewidth=1.5)
        
    g.map(plt.axhline, y=0, color='grey', linestyle='--')
    handles = [Line2D([0], [0], color=palette[l], marker='.', linestyle='-') for l in g.hue_names]
    labels = g.hue_names
    handles.append(Line2D([0], [0], color='red', linestyle=':', lw=1.5, label='수요-공급 균형점'))
    labels.append('수요-공급 균형점')
    g.add_legend(handles=handles, labels=labels, title="개선 효과")
    
    g.fig.suptitle(title, fontsize=20, y=1.03)
    g.set_axis_labels("라운드당 평균 요청 건수", y_label)
    g.set_titles(row_template="컨테이너 비용=${row_name}", col_template="포워더 수={col_name}명")
    g.fig.tight_layout(rect=[0, 0, 0.88, 0.95])
    plt.savefig(os.path.join(SAVE_DIRECTORY, filename), dpi=300)
    plt.close()

# ==============================================================================
# 셀 3: 메인 실행부
# ==============================================================================
if __name__ == "__main__":
    setup_korean_font()
    df_raw = load_data(PKL_FILENAME)

    if df_raw is not None:
        os.makedirs(SAVE_DIRECTORY, exist_ok=True)
        
        print("\n수요-공급 균형점 계산 중...")
        sim_cbms = [max(AVG_CBM_MINIMUM, int(np.random.beta(a=CBM_DISTRIBUTION_PARAMS['a'], b=CBM_DISTRIBUTION_PARAMS['b'])*AVG_CBM_CALCULATION_RANGE)) for _ in range(10000)]
        avg_cbm = np.mean(sim_cbms)
        f_counts = sorted(df_raw['포워더 수'].unique())
        eq_points = {f_count: (f_count * OPERATIONAL_CAPACITY) / avg_cbm for f_count in f_counts}
        print("포워더 수에 따른 수요-공급 균형점 (요청 건수):", eq_points)
        
        print("\n데이터 요약 테이블 생성 중...")
        grouping_cols = ['시나리오', '컨테이너 비용', '요청 건수', '포워더 수']
        
        # numeric_cols 리스트에서 grouping_cols에 포함된 열들을 제외하여 충돌 방지
        numeric_cols = df_raw.select_dtypes(include=np.number).columns.drop(grouping_cols, errors='ignore').tolist()
        df_summary = df_raw.groupby(grouping_cols)[numeric_cols].mean().reset_index()
        print(df_summary.head())
        
        # 7대 핵심 지표 시각화
        plot_kpi(df_summary, eq_points, '시장 총 공차율', '핵심 지표 1: 시장 총 공차율 (낭비율) 분석', '시장 총 공차율 (%)', '1_Ullage_Rate_Analysis.png')
        plot_kpi(df_summary, eq_points, '포워더 평균 순이익', '핵심 지표 2: 포워더 평균 순이익 분석', '평균 순이익 ($)', '2_Forwarder_Profit_Analysis.png')
        plot_kpi(df_summary, eq_points, '평균 운송 비용', '핵심 지표 3: 평균 화물 운송 비용 (화주 관점)', '평균 운송 비용 ($)', '3_Shipping_Cost_Analysis.png')
        plot_kpi(df_summary, eq_points, 'B2B 거래량', '핵심 지표 4: B2B 시장 거래량 분석', 'B2B 거래량', '4_B2B_Trade_Volume_Analysis.png')
        plot_kpi(df_summary, eq_points, '포워더 수익 안정성 (CV)', '핵심 지표 7: 포워더 수익 안정성 분석 (변동계수)', '수익 안정성 (CV) - 낮을수록 안정적', '7_Profit_Stability_Analysis.png')

        plot_value_add_analysis(df_summary, eq_points, '포워더 평균 순이익', 
                                "핵심 지표 5: 포워더 '수익 개선 효과' 분석 (vs 원시 시장)",
                                "수익 개선액 ($)", "5_Profit_Improvement_Analysis.png",
                                palette={'B2C 시장 도입 효과':'#fca311', 'B2B 시장 도입 효과':'#14213d'})

        plot_value_add_analysis(df_summary, eq_points, '평균 운송 비용',
                                "핵심 지표 6: 화주 '운송비용 절감 효과' 분석 (vs 원시 시장)",
                                "운송비용 변화액 ($) - 낮을수록 절감", "6_Cost_Savings_Analysis.png",
                                palette={'B2C 시장 도입 효과':'#00b4d8', 'B2B 시장 도입 효과':'#03045e'})
        
        print("\n\n🎉 모든 분석 및 시각화 작업이 성공적으로 완료되었습니다!")
        print(f"그래프는 '{SAVE_DIRECTORY}' 폴더에 저장되었습니다.")