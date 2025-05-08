-- Initial database schema for Coolstore application

-- Create PRODUCT_CATALOG table if not exists
CREATE TABLE IF NOT EXISTS PRODUCT_CATALOG (
    itemId VARCHAR(255) PRIMARY KEY,
    name VARCHAR(80),
    description TEXT,
    price DOUBLE PRECISION
);

-- Create INVENTORY table if not exists
CREATE TABLE IF NOT EXISTS INVENTORY (
    itemId VARCHAR(255) PRIMARY KEY,
    location VARCHAR(255),
    quantity INTEGER,
    link VARCHAR(255),
    CONSTRAINT fk_inventory_product FOREIGN KEY (itemId) REFERENCES PRODUCT_CATALOG(itemId)
);

-- Create ORDERS table if not exists
CREATE TABLE IF NOT EXISTS ORDERS (
    orderId SERIAL PRIMARY KEY,
    customerName VARCHAR(255),
    customerEmail VARCHAR(255),
    orderValue DOUBLE PRECISION,
    retailPrice DOUBLE PRECISION,
    discount DOUBLE PRECISION,
    shippingFee DOUBLE PRECISION,
    shippingDiscount DOUBLE PRECISION,
    TOTAL_PRICE DOUBLE PRECISION
);

-- Create ORDER_ITEMS table if not exists
CREATE TABLE IF NOT EXISTS ORDER_ITEMS (
    ID SERIAL PRIMARY KEY,
    productId VARCHAR(255),
    quantity INTEGER,
    ORDER_ID BIGINT,
    CONSTRAINT fk_orderitems_order FOREIGN KEY (ORDER_ID) REFERENCES ORDERS(orderId)
);

-- Insert sample data for PRODUCT_CATALOG
INSERT INTO PRODUCT_CATALOG (itemId, name, description, price) VALUES
('329299', 'Red Fedora', 'Official Red Hat Fedora', 34.99),
('329199', 'Forge Laptop Sticker', 'JBoss Community Forge Project Sticker', 8.50),
('165613', 'Solid Performance Polo', 'Moisture-wicking, antimicrobial 100% polyester design', 35.00),
('165614', 'Ogio Caliber Polo', 'Moisture-wicking 100% polyester', 28.75),
('165954', '16 oz. Vortex Tumbler', 'Double-wall insulated, BPA-free, acrylic cup', 6.00),
('444434', 'Pebble Smart Watch', 'Smart glasses and smart watches are perhaps two of the most exciting developments in recent years', 24.00),
('444435', 'Oculus Rift', 'The world of gaming has also undergone some very unique and compelling tech advances in recent years', 106.00),
('444436', 'Lytro Camera', 'Consumers who want to up their photography game are looking at newfangled cameras like the Lytro Field camera', 44.30)
ON CONFLICT (itemId) DO NOTHING;

-- Insert sample data for INVENTORY
INSERT INTO INVENTORY (itemId, location, quantity, link) VALUES
('329299', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('329199', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('165613', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('165614', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('165954', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('444434', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('444435', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh'),
('444436', 'Raleigh', 100, 'http://maps.google.com/?q=Raleigh')
ON CONFLICT (itemId) DO NOTHING;