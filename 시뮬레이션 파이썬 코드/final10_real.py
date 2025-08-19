# final10_real.py (collect_results 함수가 수정된 최종 버전)

# ----------------- 필수 라이브러리 (변경 없음) -----------------
import random, numpy as np, math, pandas as pd, pickle, time, os, itertools
from tqdm import tqdm
import warnings
warnings.filterwarnings('ignore')
import multiprocessing

# ==============================================================================
# 1. 시뮬레이션 환경 설정 (변경 없음)
# ==============================================================================
CONTAINER_CAPACITY = 26.0
NUM_ROUNDS = 50
INITIAL_CAPITAL = 3000.0
OPERATIONAL_CAPACITY = 52.0
CBM_DISTRIBUTION_PARAMS = {'a': 1, 'b': 15}
PRIMITIVE_MARKET_ACCESS_LIMIT = 3
B2B_CONFIDENCE_FACTOR = 0.8 

# ==============================================================================
# 2. 핵심 데이터 구조 및 클래스 정의 (변경 없음)
# ==============================================================================
class Contract:
    def __init__(self, contract_id, cbm):
        self.id, self.cbm, self.revenue, self.owner, self.bids = contract_id, cbm, 0.0, None, []
    def __repr__(self): return f"Contract(ID:{self.id}, CBM:{self.cbm}, Revenue:{self.revenue:.2f})"

class Forwarder:
    def __init__(self, forwarder_id, strategy, container_unit_cost):
        self.id, self.strategy, self.container_unit_cost = forwarder_id, strategy, container_unit_cost
        self.capital, self.capacity, self.portfolio, self.round_profit = INITIAL_CAPITAL, OPERATIONAL_CAPACITY, [], 0.0
        if self.strategy == 'aggressive': self.target_profit_margin = 0.10
        elif self.strategy == 'conservative': self.target_profit_margin = 0.30
        else: self.target_profit_margin = 0.20
    def get_current_load_cbm(self): return sum(c.cbm for c in self.portfolio)
    def estimate_marginal_cost(self, new_contract_cbm):
        current_load = self.get_current_load_cbm()
        containers_before = math.ceil(current_load / CONTAINER_CAPACITY) if current_load > 0 else 0
        containers_after = math.ceil((current_load + new_contract_cbm) / CONTAINER_CAPACITY)
        return (containers_after - containers_before) * self.container_unit_cost
    def bid_on_b2c_request(self, contract_request, market_scenario):
        if self.get_current_load_cbm() + contract_request.cbm > self.capacity * 1.2: return
        marginal_cost = self.estimate_marginal_cost(contract_request.cbm)
        if market_scenario != 'B2B_Enabled_Market':
            base_cost = (contract_request.cbm * (self.container_unit_cost / CONTAINER_CAPACITY) * 1.1) if marginal_cost == 0 else marginal_cost
            if self.strategy == 'aggressive': margin_range = (1.05, 1.15)
            elif self.strategy == 'conservative': margin_range = (1.25, 1.40)
            else: margin_range = (1.1, 1.3)
            my_bid_price = base_cost * random.uniform(*margin_range)
        else:
            base_cost = (contract_request.cbm * (self.container_unit_cost / CONTAINER_CAPACITY) * 1.2)
            target_price = base_cost * (1 + self.target_profit_margin)
            final_cost = max(marginal_cost, target_price)
            price_adjustment_factor = 1.0 - (B2B_CONFIDENCE_FACTOR * random.uniform(0.0, 0.05))
            my_bid_price = final_cost * price_adjustment_factor
        contract_request.bids.append({'forwarder': self, 'price': my_bid_price})
    def determine_b2b_sale_list(self, market):
        sell_trigger_load = self.get_current_load_cbm()
        if self.strategy == 'aggressive': should_sell = sell_trigger_load > self.capacity * 1.1
        elif self.strategy == 'conservative': should_sell = sell_trigger_load > self.capacity
        else: should_sell = sell_trigger_load > self.capacity
        if should_sell and self.portfolio:
            cbm_to_sell = self.get_current_load_cbm() - self.capacity
            sorted_portfolio = sorted(self.portfolio, key=lambda c: c.revenue / c.cbm if c.cbm > 0 else float('inf'))
            cbm_sold = 0
            for contract in sorted_portfolio:
                if cbm_sold >= cbm_to_sell: break
                reserve_price_multiplier = 0.75 if self.strategy == 'conservative' else 0.85
                reserve_price = contract.revenue * reserve_price_multiplier
                market.list_contract_for_b2b_sale(contract, reserve_price, self)
                cbm_sold += contract.cbm
    def place_bids_on_b2b_market(self, market):
        for sale_item in market.b2b_for_sale:
            contract = sale_item['contract']
            if sale_item['seller'] == self or self.get_current_load_cbm() + contract.cbm > self.capacity: continue
            marginal_cost_if_bought = self.estimate_marginal_cost(contract.cbm)
            willingness_to_pay = contract.revenue - marginal_cost_if_bought
            if willingness_to_pay > 0:
                if self.strategy == 'aggressive': bid_multiplier_range = (0.90, 1.0)
                elif self.strategy == 'conservative':
                    if marginal_cost_if_bought > 0: continue
                    bid_multiplier_range = (0.70, 0.85)
                else: bid_multiplier_range = (0.80, 0.95)
                my_bid_price = willingness_to_pay * random.uniform(*bid_multiplier_range)
                if my_bid_price >= sale_item['reserve_price']: market.place_b2b_bid(sale_item, my_bid_price, self)
    def __repr__(self): return f"Fwd-{self.id}({self.strategy[:3]})"

class TheChaumPlusPlatform:
    def __init__(self, forwarders, requests_per_round, scenario):
        self.forwarders, self.requests_per_round, self.scenario = forwarders, requests_per_round, scenario
        self.b2c_open_requests, self.b2b_for_sale = [], []
        self.log = { 'total_requests': 0, 'failed_requests': 0, 'successful_contracts': [], 'b2b_trades': 0, 'total_ullage': 0 }
    def generate_new_requests(self):
        self.b2c_open_requests = []
        num_requests = random.randint(self.requests_per_round - 5, self.requests_per_round + 5)
        for i in range(num_requests):
            cbm = max(3, int(np.random.beta(a=CBM_DISTRIBUTION_PARAMS['a'], b=CBM_DISTRIBUTION_PARAMS['b']) * 30))
            self.b2c_open_requests.append(Contract(f"R{self.log['total_requests']+i}", cbm))
        self.log['total_requests'] += num_requests
    def run_b2c_market_round(self):
        participating_forwarders = self.forwarders
        if self.scenario == 'Primitive_Market':
            if len(self.forwarders) > PRIMITIVE_MARKET_ACCESS_LIMIT:
                participating_forwarders = random.sample(self.forwarders, PRIMITIVE_MARKET_ACCESS_LIMIT)
        for request in self.b2c_open_requests:
            for f in participating_forwarders: f.bid_on_b2c_request(request, self.scenario)
            if request.bids:
                winner_bid = min(request.bids, key=lambda b: b['price'])
                winner, price = winner_bid['forwarder'], winner_bid['price']
                request.owner, request.revenue = winner, price
                winner.portfolio.append(request)
                self.log['successful_contracts'].append(request)
            else: self.log['failed_requests'] += 1
    def list_contract_for_b2b_sale(self, contract, reserve_price, seller): self.b2b_for_sale.append({'contract': contract, 'reserve_price': reserve_price, 'seller': seller, 'bids': []})
    def place_b2b_bid(self, sale_item, bid_price, buyer): sale_item['bids'].append({'buyer': buyer, 'price': bid_price})
    def run_b2b_auction_market(self):
        self.b2b_for_sale = []
        for f in self.forwarders: f.determine_b2b_sale_list(self)
        for f in self.forwarders: f.place_bids_on_b2b_market(self)
        for sale_item in self.b2b_for_sale:
            if not sale_item['bids']: continue
            highest_bid = max(sale_item['bids'], key=lambda b: b['price'])
            if highest_bid['price'] >= sale_item['reserve_price']:
                winner, final_price, seller, contract = highest_bid['buyer'], highest_bid['price'], sale_item['seller'], sale_item['contract']
                seller.round_profit, winner.round_profit = seller.round_profit + final_price, winner.round_profit - final_price
                if contract in seller.portfolio: seller.portfolio.remove(contract)
                winner.portfolio.append(contract)
                contract.owner = winner
                self.log['b2b_trades'] += 1
    def finalize_round(self):
        for f in self.forwarders:
            total_load = f.get_current_load_cbm()
            num_containers = math.ceil(total_load / CONTAINER_CAPACITY) if total_load > 0 else 0
            total_container_cost, total_revenue = num_containers * f.container_unit_cost, sum(c.revenue for c in f.portfolio)
            f.round_profit += total_revenue - total_container_cost
            f.capital += f.round_profit
            capacity_provided = num_containers * CONTAINER_CAPACITY
            self.log['total_ullage'] += (capacity_provided - total_load)
            f.round_profit, f.portfolio = 0.0, []

class Simulation:
    def __init__(self, scenario, requests_per_round, container_unit_cost, num_forwarders, forwarder_strategy_ratio):
        self.scenario, self.forwarders = scenario, []
        f_id=0
        total_ratio=sum(forwarder_strategy_ratio.values())
        for strategy, ratio in forwarder_strategy_ratio.items():
            count = round(num_forwarders*(ratio/total_ratio))
            for _ in range(count): self.forwarders.append(Forwarder(f_id, strategy, container_unit_cost)); f_id+=1
        while len(self.forwarders) < num_forwarders: self.forwarders.append(Forwarder(len(self.forwarders), 'rational', container_unit_cost))
        self.forwarders = self.forwarders[:num_forwarders]
        self.platform = TheChaumPlusPlatform(self.forwarders, requests_per_round, scenario)
        
    def run(self):
        all_forwarders_round_profits = []
        for _ in range(NUM_ROUNDS):
            self.platform.generate_new_requests()
            self.platform.run_b2c_market_round()
            if self.scenario == 'B2B_Enabled_Market': self.platform.run_b2b_auction_market()
            self.platform.finalize_round()
            all_forwarders_round_profits.append([f.capital for f in self.forwarders])
        return self.collect_results(all_forwarders_round_profits)

    # [수정됨] 모든 KPI를 계산하도록 완전한 버전으로 복구
    def collect_results(self, all_forwarders_round_profits):
        final_capitals = all_forwarders_round_profits[-1] if all_forwarders_round_profits else [f.capital for f in self.forwarders]
        if all_forwarders_round_profits:
            capital_array = np.array(all_forwarders_round_profits)
            std_dev_per_forwarder = np.std(capital_array, axis=0)
            avg_std_dev = np.mean(std_dev_per_forwarder)
            mean_final_capital = np.mean(final_capitals)
            profit_stability_cv = (avg_std_dev / mean_final_capital) if mean_final_capital > 0 else 0
        else: profit_stability_cv = 0

        log = self.platform.log
        total_successful_cbm = sum(c.cbm for c in log['successful_contracts'])
        total_capacity_used = log['total_ullage'] + total_successful_cbm
        results = { "포워더 평균 순이익": np.mean(final_capitals) - INITIAL_CAPITAL if final_capitals else 0, "운송 성공률": (len(log['successful_contracts']) / log['total_requests'] * 100) if log['total_requests'] > 0 else 0, "시장 총 공차율": (log['total_ullage'] / total_capacity_used * 100) if total_capacity_used > 0 else 0, "B2B 거래량": log['b2b_trades'], "평균 운송 비용": (sum(c.revenue for c in log['successful_contracts']) / len(log['successful_contracts'])) if log['successful_contracts'] else 0, "포워더 수익 안정성 (CV)": profit_stability_cv }
        for strategy_type in ['aggressive', 'conservative', 'rational']:
            strategy_indices = [i for i, f in enumerate(self.forwarders) if f.strategy == strategy_type]
            if strategy_indices:
                strategy_capitals = [final_capitals[i] for i in strategy_indices]
                results[f'{strategy_type} 평균 순이익'] = np.mean(strategy_capitals) - INITIAL_CAPITAL
        return results

def run_single_simulation(params):
    scenario, cost, requests, f_count, num_iterations, f_strategy_ratio = params
    iteration_results = []
    for i in range(num_iterations):
        sim = Simulation(scenario, requests, cost, f_count, f_strategy_ratio)
        result = sim.run()
        result.update({'시나리오': scenario, '컨테이너 비용': cost, '요청 건수': requests, '포워더 수': f_count, '반복 ID': i})
        iteration_results.append(result)
    return iteration_results

if __name__ == "__main__":
    scenarios = ['Primitive_Market', 'B2C_Open_Market', 'B2B_Enabled_Market']
    request_levels = [25, 50, 75, 100, 125, 150, 175, 200]
    cost_levels = [1000, 1500, 2000]
    forwarder_counts = [10, 15, 20]
    NUM_ITERATIONS = 50
    forwarder_strategy_ratio = {'rational': 5, 'aggressive': 3, 'conservative': 2}
    
    tasks = [(sc, co, re, fc, NUM_ITERATIONS, forwarder_strategy_ratio) for sc, co, re, fc in itertools.product(scenarios, cost_levels, request_levels, forwarder_counts)]
    
    print("Final10 Real: 현실적 혼합 경쟁 모델 시뮬레이션을 시작합니다.")
    n_cores = multiprocessing.cpu_count()
    print(f"총 조합: {len(tasks)}개, 각 조합당 {NUM_ITERATIONS}회 반복")
    
    all_results_data = []
    with multiprocessing.Pool(processes=n_cores) as pool:
        results_iterator = pool.imap_unordered(run_single_simulation, tasks)
        for result_list in tqdm(results_iterator, total=len(tasks)):
            all_results_data.extend(result_list)
            
    df_raw_data = pd.DataFrame(all_results_data)
    
    with open("final10_real.pkl", 'wb') as file:
        pickle.dump(df_raw_data, file)
    print("\n[성공] 모든 시뮬레이션 결과가 'final10_real.pkl'에 저장되었습니다.")