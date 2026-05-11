-- Add default sales role user for quick demo
INSERT INTO users(username, password_hash, real_name, email, role, status)
SELECT 'sales01', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '销售张三', 'sales01@example.com', 'SALES', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'sales01');

INSERT INTO users(username, password_hash, real_name, email, role, status)
SELECT 'manager01', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '经理李四', 'manager01@example.com', 'MANAGER', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'manager01');

INSERT INTO users(username, password_hash, real_name, email, role, status)
SELECT 'finance01', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '财务王五', 'finance01@example.com', 'FINANCE', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'finance01');

-- Add sample customers for demo
INSERT INTO customers(code, name, grade, source, industry, scale, province, city, address, phone, email, website, status, stage, owner_user_id, remark)
SELECT 'C001', '星辰科技有限公司', 'A', '展会', '软件开发', '100-500人', '北京', '北京市', '海淀区中关村大街1号', '010-88886666', 'contact@xingchen.com', 'www.xingchen.com', 'FOLLOWING', 'PROPOSAL', (SELECT id FROM users WHERE username = 'sales01' LIMIT 1), '正在评估我们的CRM方案，预计下月签单'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code = 'C001');

INSERT INTO customers(code, name, grade, source, industry, scale, province, city, address, phone, email, status, stage, owner_user_id, remark, next_follow_time)
SELECT 'C002', '蓝天制造集团', 'B', '老客户推荐', '制造业', '500-1000人', '上海', '上海市', '浦东新区张江路88号', '021-55551111', 'info@lantian.com', 'POTENTIAL', 'INITIAL_CONTACT', (SELECT id FROM users WHERE username = 'sales01' LIMIT 1), '通过老客户王总推荐，初次接触', datetime('now','+2 days')
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code = 'C002');

INSERT INTO customers(code, name, grade, source, industry, scale, province, city, address, phone, email, status, stage, owner_user_id, remark)
SELECT 'C003', '华夏金融控股', 'A', '线上推广', '金融', '1000人以上', '深圳', '深圳市', '南山区科技园路66号', '0755-33332222', 'hr@huaxiafinance.com', 'DEAL', 'MAINTENANCE', (SELECT id FROM users WHERE username = 'manager01' LIMIT 1), '已签约年费客户，定期维护关系'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE code = 'C003');
