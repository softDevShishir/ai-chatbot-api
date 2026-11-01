-- Fictional demo catalogue for Aronno Mart, an online store in Bangladesh.
-- All names, descriptions and prices (BDT) are made up for this project.

INSERT INTO products (name, description, category, price, in_stock) VALUES
    ('Bata Comfit Running Shoes',   'Lightweight mesh running shoes with cushioned sole for daily jogging and gym workouts. Available in black and blue, sizes 39-45.', 'shoes', 3499.00, TRUE),
    ('Apex Leather Formal Shoes',   'Genuine leather Oxford formal shoes in brown and black, ideal for office wear and weddings. Sizes 40-44.', 'shoes', 5990.00, TRUE),
    ('Canvas Casual Sneakers',      'Classic white canvas sneakers with rubber sole, comfortable for everyday casual wear. Unisex, sizes 36-44.', 'shoes', 1850.00, TRUE),
    ('Kids School Shoes',           'Durable black school shoes for kids with velcro strap and anti-slip sole. Sizes 28-35.', 'shoes', 1290.00, FALSE),
    ('Leather Sandals for Men',     'Handmade leather sandals with padded footbed, perfect for summer and Eid. Brown, sizes 39-44.', 'shoes', 1650.00, TRUE),
    ('Cotton Panjabi',              'Premium cotton panjabi with embroidered collar for Eid and festive occasions. Colours: white, off-white, navy. Sizes M-XXL.', 'clothing', 2450.00, TRUE),
    ('Slim Fit Formal Shirt',       'Wrinkle-resistant slim fit formal shirt in light blue and white, long sleeves, suitable for office wear. Sizes S-XL.', 'clothing', 1590.00, TRUE),
    ('Casual Check Shirt',          'Soft cotton flannel check shirt, half sleeves, red and green patterns for casual outings. Sizes M-XL.', 'clothing', 1190.00, TRUE),
    ('Polo T-Shirt',                'Breathable pique cotton polo t-shirt with ribbed collar. Colours: black, maroon, grey. Sizes S-XXL.', 'clothing', 890.00, TRUE),
    ('Linen Summer Shirt',          'Lightweight linen shirt that keeps you cool in hot and humid weather. Beige and sky blue, long sleeves. Sizes M-XL.', 'clothing', 2190.00, FALSE),
    ('Leather Laptop Bag',          'Genuine leather laptop bag fits up to 15.6 inch laptops, with padded compartment and shoulder strap. Dark brown.', 'bags', 6500.00, TRUE),
    ('Waterproof Travel Backpack',  'Waterproof 35 litre backpack with USB charging port, laptop sleeve and multiple pockets, great for travel and monsoon season.', 'bags', 3290.00, TRUE),
    ('Jute Tote Bag',               'Eco-friendly handmade jute tote bag from Bangladesh, strong handles, ideal for shopping and daily use.', 'bags', 450.00, TRUE),
    ('Ladies Leather Handbag',      'Elegant leather handbag for women with zip closure and detachable strap. Colours: black, tan, red.', 'bags', 4200.00, TRUE),
    ('Kids School Backpack',        'Lightweight school backpack for kids with cartoon print, water bottle pocket and padded straps.', 'bags', 1150.00, FALSE);

INSERT INTO store_policies (topic, content) VALUES
    ('returns',
     'Items can be returned within 7 days of delivery if unused, in original packaging, and with the receipt. Refunds are issued to the original payment method within 5-7 business days after we receive the returned item.'),
    ('shipping',
     'We deliver across Bangladesh. Inside Dhaka, delivery takes 1-2 business days; outside Dhaka, 3-5 business days. Standard shipping is 60 BDT and free on orders over 2000 BDT.'),
    ('payment-methods',
     'We accept Cash on Delivery (COD), bKash, Nagad, Rocket, and all major Visa/Mastercard credit and debit cards. Full payment is required at checkout for card payments; COD is settled on delivery.'),
    ('warranty',
     'Electronics and appliances carry a 1-year manufacturer warranty against defects. Shoes and bags carry a 30-day warranty against manufacturing defects (not normal wear and tear). Clothing has no warranty but is covered by our returns policy.'),
    ('opening-hours',
     'Our online store takes orders 24/7. Customer support is available Saturday to Thursday, 10:00 AM to 8:00 PM (Asia/Dhaka time). We are closed on Fridays and government holidays.'),
    ('contact',
     'For help, email support@aronnomart.example or call our hotline at 09612-345678 (Saturday-Thursday, 10 AM-8 PM). You can also message us on Facebook at facebook.com/aronnomart.'),
    ('exchange',
     'Size or colour exchanges are free within 7 days of delivery for unused items in original packaging. Visit any of our pickup points or request a pickup through customer support; one free exchange is allowed per order.'),
    ('order-cancellation',
     'Orders can be cancelled free of charge before they are shipped. Once an order has shipped, it can no longer be cancelled but may be returned after delivery under our returns policy.');
