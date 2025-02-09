package com.revshopp2.Order.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.util.UriComponentsBuilder;

import com.revshopp2.Order.model.Buyer;
import com.revshopp2.Order.model.Cart;
import com.revshopp2.Order.model.Order_Detail;
import com.revshopp2.Order.model.Orders;
import com.revshopp2.Order.model.Product;
import com.revshopp2.Order.model.ReceivedOrders;
import com.revshopp2.Order.model.Review;
import com.revshopp2.Order.model.ReviewForBuyer;
import com.revshopp2.Order.model.ReviewProducts;
import com.revshopp2.Order.model.Seller;
import com.revshopp2.Order.model.displayMyOrders;
import com.revshopp2.Order.service.EmailService;
import com.revshopp2.Order.service.OrderService;
import com.revshopp2.Order.service.OrderdetailService;
import com.revshopp2.Order.service.ReviewService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@Controller
@RequestMapping("/order")
public class Ordercontroller {

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderdetailService orderdetailService;

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private EmailService emailService;
	
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @GetMapping("/CompletedOrders")
    public String showCompletedOrders(Model model, HttpServletRequest request) {
        Long sellerId = getSellerIdFromCookies1(request);
        
        // Redirect to login if sellerId is null
        if (sellerId == null) {
            return "redirect:http://localhost:9988/ecom/LoginPage";
        }

        List<Order_Detail> orders = orderdetailService.getOrdersBySellerId(sellerId);
        List<ReceivedOrders> receivedOrdersList = new ArrayList<>();

        for (Order_Detail order : orders) {
            ReceivedOrders receivedOrder = new ReceivedOrders();
            receivedOrder.setOrderId(order.getOrder().getOrderId());
            receivedOrder.setOrderDate(order.getOrder().getOrderDate());
            receivedOrder.setPrice(order.getPrice_per_unit());
            receivedOrder.setQuantity(order.getQuantity());
            receivedOrder.setStatus(order.getStatus());

            // Fetch product details
            String productUrl = "http://localhost:9988/products/cartController/" + order.getProductId();
            try {
                ResponseEntity<Product> productResponse = restTemplate.getForEntity(productUrl, Product.class);
                Product product = productResponse.getBody();
                if (product != null) {
                    receivedOrder.setImage(product.getImage());
                    receivedOrder.setProductName(product.getProductName());
                }
            } catch (Exception e) {
                System.out.println("Error fetching product data: " + e.getMessage());
            }

            // Fetch buyer details
            String buyerUrl = "http://localhost:9988/ecom/sellerController?buyerId=" + order.getOrder().getBuyerId();
            try {
                ResponseEntity<Buyer> buyerResponse = restTemplate.getForEntity(buyerUrl, Buyer.class);
                Buyer buyer = buyerResponse.getBody();
                if (buyer != null) {
                    receivedOrder.setName(buyer.getFirstName() + " " + buyer.getLastName());
                    receivedOrder.setAddress(
                        buyer.getStreet() + "\n" + buyer.getCity() + "\n" + buyer.getState()
                    );
                }
            } catch (Exception e) {
                System.out.println("Error fetching buyer data: " + e.getMessage());
            }

            receivedOrdersList.add(receivedOrder);
        }

        // Add the final list to the model
        model.addAttribute("orders", receivedOrdersList);

        return "Seller_CompletedOrder"; // Thymeleaf template for completed orders
    }

    // Method to retrieve seller ID from cookies
    private Long getSellerIdFromCookies1(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("sellerId".equals(cookie.getName())) {
                    return Long.parseLong(cookie.getValue());
                }
            }
        }
        return null;
    }
	@Transactional
	@PostMapping("/checkout/confirm")
	public String confirmCheckout(@RequestParam String paymentMethod, HttpServletRequest request, Model model) {
		Long buyerId = getBuyerIdFromCookies(request);
		if (buyerId != null) {
			// Cart service URL to fetch cart items
			String cartServiceUrl2 = "http://localhost:9988/ecom/sellerController" + "?buyerId=" + buyerId;
			ResponseEntity<Buyer> responseEntity2 = restTemplate.getForEntity(cartServiceUrl2, Buyer.class);
			Buyer buyer = responseEntity2.getBody();
			String deliveryAddress = buyer.getStreet() + " " + buyer.getCity() + " " + buyer.getState();
			StringBuilder message = new StringBuilder();
			message.append("<html><body>");
			message.append(
					"<img src='https://www.revshop.com/images/main-logo.svg' style='width:200px;height:100px;' >");

			message.append("<p>Hello ").append(buyer.getFirstName() + " " + buyer.getLastName()).append(",</p>");
			message.append("<p>Your order has been placed successfully!</p>");
			message.append("<p><b>Delivery Address:</b> ").append(deliveryAddress).append("</p>");
			message.append("<p><b>Order Details:</b></p>");
			message.append("<table border='1' cellpadding='5' cellspacing='0'>");
			message.append(
					"<tr><th>Product Image</th><th>Product Name</th><th>Quantity</th><th>Price/Unit</th><th>Total</th></tr>");
			String cartServiceUrl = "http://localhost:9988/cart/allproductController/" + buyerId;
			ResponseEntity<Cart[]> responseEntity = restTemplate.getForEntity(cartServiceUrl, Cart[].class);
			if (responseEntity.getStatusCode().is2xxSuccessful()) {
				// Convert Cart[] to List<Cart> to use stream
				List<Product> productItems = new ArrayList<>();
				List<Cart> cartItems = Arrays.asList(responseEntity.getBody());
				double totalPrice = 0;
				for (Cart cartItem : cartItems) {
					String cartServiceUrl3 = "http://localhost:9988/products/cartController/" + cartItem.getProductId();
					ResponseEntity<Product> responseEntity3 = restTemplate.getForEntity(cartServiceUrl3, Product.class);
					Product product = responseEntity3.getBody();
					totalPrice += (product.getPrice() - product.getDiscountPrice()) * cartItem.getQuantity();
				}

				Orders order = orderService.createOrder(buyer, totalPrice, buyer.getStreet());
				for (Cart cartItem : cartItems) {
					String cartServiceUrl4 = "http://localhost:9988/products/cartController/" + cartItem.getProductId();
					ResponseEntity<Product> responseEntity4 = restTemplate.getForEntity(cartServiceUrl4, Product.class);
					Product product = responseEntity4.getBody();

					Order_Detail orderDetails = new Order_Detail(order, product.getProductId(), cartItem.getQuantity(),
							product.getPrice(), product.getSellerId(), "Placed");
					orderdetailService.addOrderDetails(orderDetails);

					int updatedStock = product.getQuantity() - cartItem.getQuantity();
					product.setQuantity(updatedStock);
					productItems.add(product);
					String cartServiceUrl3 = "http://localhost:9988/products/updateProduct";

					// Append query parameters to the URL
					String urlWithParams = cartServiceUrl3 + "?productId=" + product.getProductId() + "&quantity="
							+ updatedStock;

					// Send GET request with query parameters
					ResponseEntity<Boolean> response = restTemplate.getForEntity(urlWithParams, Boolean.class);


					String cartServiceUrl5 = "http://localhost:9988/cart/removefromcart" + "?buyerId=" + buyerId
							+ "&productId=" + product.getProductId();
					// Send GET request with query parameters
					ResponseEntity<Boolean> response1 = restTemplate.getForEntity(cartServiceUrl5, Boolean.class);
					message.append("<tr>");
					message.append("<td>").append("<img src='").append(product.getImage())
							.append("' alt='Product Image' style='width:125px;height:100px;' />").append("</td>");
					message.append("<td>").append(product.getProductName()).append("</td>");
					message.append("<td>").append(cartItem.getQuantity()).append("</td>");
					message.append(String.format("<td>%.2f</td>", (product.getPrice() - product.getDiscountPrice())));
					message.append(String.format("<td>%.2f</td>", totalPrice));
					message.append("</tr>");
					if (product.getQuantity() <= product.getThreshold()) {
						notifySeller(product);
					}
					;
					message.append("</table>");
					message.append("</body></html>");
					model.addAttribute("orderSummary", productItems);
					model.addAttribute("totalPrice", totalPrice);
					model.addAttribute("paymentMethod", paymentMethod);
					model.addAttribute("buyer", buyer);
					messagingTemplate.convertAndSend("/topic/orders/"+product.getSellerId(),"Ordered "+product.getProductName());

				}
				emailService.sendEmail(buyer.getEmail(), "Order Placed Successfully!", message.toString());
				//websocket notification
			} else {
				// Handle error response
				model.addAttribute("error", "Failed to retrieve cart items.");
			}
			//web socket

		}
		return "order-confirmation";
	}
//    @Transactional
//    @PostMapping("/checkout/confirm")
//    public String confirmCheckout(@RequestParam String paymentMethod, HttpServletRequest request, Model model) {
//        Long buyerId = getBuyerIdFromCookies(request);
//        if (buyerId != null) {
//            try {
//                // Fetch Buyer information
//                String cartServiceUrl2 = "http://localhost:9988/ecom/sellerController?buyerId=" + buyerId;
//                ResponseEntity<Buyer> responseEntity2 = restTemplate.getForEntity(cartServiceUrl2, Buyer.class);
//                Buyer buyer = responseEntity2.getBody();
//                if (buyer == null) {
//                    throw new RuntimeException("Buyer information not found.");
//                }
//
//                String deliveryAddress = buyer.getStreet() + " " + buyer.getCity() + " " + buyer.getState();
//                StringBuilder message = new StringBuilder();
//                message.append("<html><body>")
//                       .append("<img src='https://www.revshop.com/images/main-logo.svg' style='width:200px;height:100px;' >")
//                       .append("<p>Hello ").append(buyer.getFirstName()).append(" ").append(buyer.getLastName()).append(",</p>")
//                       .append("<p>Your order has been placed successfully!</p>")
//                       .append("<p><b>Delivery Address:</b> ").append(deliveryAddress).append("</p>")
//                       .append("<p><b>Order Details:</b></p>")
//                       .append("<table border='1' cellpadding='5' cellspacing='0'>")
//                       .append("<tr><th>Product Image</th><th>Product Name</th><th>Quantity</th><th>Price/Unit</th><th>Total</th></tr>");
//
//                // Fetch Cart items
//                String cartServiceUrl = "http://localhost:9988/cart/allproductController/" + buyerId;
//                ResponseEntity<Cart[]> responseEntity = restTemplate.getForEntity(cartServiceUrl, Cart[].class);
//                if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
//                    throw new RuntimeException("Failed to retrieve cart items.");
//                }
//
//                List<Product> productItems = new ArrayList<>();
//                List<Cart> cartItems = Arrays.asList(responseEntity.getBody());
//                double totalPrice = 0;
//
//                for (Cart cartItem : cartItems) {
//                    String cartServiceUrl3 = "http://localhost:9988/products/cartController/" + cartItem.getProductId();
//                    ResponseEntity<Product> responseEntity3 = restTemplate.getForEntity(cartServiceUrl3, Product.class);
//                    Product product = responseEntity3.getBody();
//                    if (product == null) {
//                        throw new RuntimeException("Product details not found for product ID: " + cartItem.getProductId());
//                    }
//
//                    double itemTotalPrice = (product.getPrice() - product.getDiscountPrice()) * cartItem.getQuantity();
//                    totalPrice += itemTotalPrice;
//
//                    Orders order = orderService.createOrder(buyer, totalPrice, buyer.getStreet());
//                    Order_Detail orderDetails = new Order_Detail(order, product.getProductId(), cartItem.getQuantity(),
//                            product.getPrice(), product.getSellerId(), "Placed");
//                    orderdetailService.addOrderDetails(orderDetails);
//
//                    int updatedStock = product.getQuantity() - cartItem.getQuantity();
//                    product.setQuantity(updatedStock);
//                    productItems.add(product);
//
//                    // Update product stock
//                    String cartServiceUrl31 = "http://localhost:9988/products/updateProduct";
//                    String urlWithParams = cartServiceUrl31 + "?productId=" + product.getProductId() + "&quantity=" + updatedStock;
//                    restTemplate.getForEntity(urlWithParams, Boolean.class);
//
//                    // Remove item from cart
//                    String cartServiceUrl5 = "http://localhost:9988/cart/removefromcart?buyerId=" + buyerId + "&productId=" + product.getProductId();
//                    restTemplate.getForEntity(cartServiceUrl5, Boolean.class);
//
//                    message.append("<tr>")
//                           .append("<td><img src='").append(product.getImage()).append("' alt='Product Image' style='width:125px;height:100px;' /></td>")
//                           .append("<td>").append(product.getProductName()).append("</td>")
//                           .append("<td>").append(cartItem.getQuantity()).append("</td>")
//                           .append(String.format("<td>%.2f</td>", (product.getPrice() - product.getDiscountPrice())))
//                           .append(String.format("<td>%.2f</td>", itemTotalPrice))
//                           .append("</tr>");
//
//                    if (product.getQuantity() <= product.getThreshold()) {
//                        notifySeller(product);
//                    }
//
//                    messagingTemplate.convertAndSend("/topic/orders/" + product.getSellerId(), "Ordered " + product.getProductName());
//                }
//
//                message.append("</table></body></html>");
//                emailService.sendEmail(buyer.getEmail(), "Order Placed Successfully!", message.toString());
//
//                model.addAttribute("orderSummary", productItems);
//                model.addAttribute("totalPrice", totalPrice);
//                model.addAttribute("paymentMethod", paymentMethod);
//                model.addAttribute("buyer", buyer);
//
//            } catch (Exception e) {
//                // Log and set error message
//                model.addAttribute("error", "An error occurred during order confirmation: " + e.getMessage());
//                return "error";
//            }
//        }
//        return "order-confirmation";
//    }

	private void notifySeller(Product product) {
		String getSellerObj = "http://localhost:9988/seller/sellerController" + "?sellerId=" + product.getSellerId();
		ResponseEntity<Seller> seller = restTemplate.getForEntity(getSellerObj, Seller.class);
		Seller sellerobj = seller.getBody();
		String subject = "Low Stock Alert for Product ID: " + product.getProductId();

		// Build HTML formatted notification message
		StringBuilder notification = new StringBuilder();

		notification.append("<html><body>");
		notification.append("<p>Dear ").append(sellerobj.getFirstName() + " " + sellerobj.getLastName())
				.append(",</p>");
		notification.append("<p>The stock for Product ID: <b>").append(product.getProductId());
		notification.append("<p><b>Product Image:</b></p>");
		notification.append("<img src='").append(product.getImage())
				.append("' alt='Product Image' style='width:200px;height:200px;' />").append("<br>")
				.append(product.getProductName()).append(") is running low.</p>");
		notification.append("<p><b>Current stock:</b> ").append(product.getQuantity()).append("</p>");
		notification.append("<p>Please restock soon to meet demand.</p>");
		notification.append("<p>- Revshop</p>");
		notification.append("</body></html>");

		// Send email to seller
		emailService.sendEmail(sellerobj.getEmail(), subject, notification.toString());
	}

	@GetMapping("/buyer")
	public String getOrdersByBuyer(Model model, HttpServletRequest request) {
	    Long buyerId = getBuyerIdFromCookies(request);

	    // Fetch all orders associated with the buyer
	    List<Orders> ordersItem = orderService.getOrdersByBuyerId(buyerId);

	    // Prepare list of displayMyOrders objects to send to the view
	    List<displayMyOrders> displayOrders = new ArrayList<>();
	    List<Boolean> orderReviewStatuses = new ArrayList<>();

	    // Loop through each order and its order details
	    for (Orders order : ordersItem) {
	        List<Order_Detail> singleOrderDetails = orderdetailService
	                .getOrderDetailByOrderId(order.getTransaction_id());

	        // For each order detail, create a displayMyOrders object
	        for (Order_Detail orderDetail : singleOrderDetails) {
	            displayMyOrders dmo = new displayMyOrders();
	            dmo.setOrderId(order.getTransaction_id());
	            String productServiceUrl = "http://localhost:9988/products/cartController/" + orderDetail.getProductId();

	            try {
	                ResponseEntity<Product> response = restTemplate.getForEntity(productServiceUrl, Product.class);
	                if (response.getStatusCode().is2xxSuccessful()) {
	                    Product product = response.getBody();
	                    if (product != null) {
	                        dmo.setProductId(product.getProductId());
	                        dmo.setImage(product.getImage());
	                        dmo.setPrice(product.getPrice());
	                        dmo.setProductName(product.getProductName());
	                        dmo.setStatus(orderDetail.getStatus());
	                        displayOrders.add(dmo);
	                    }
	                } else {
	                    System.err.println("Non-successful response from product service: " + response.getStatusCode());
	                }
	            } catch (UnknownContentTypeException e) {
	                System.err.println("Unexpected content type, unable to parse response for Product ID "
	                                   + orderDetail.getProductId() + ": " + e.getMessage());
	            } catch (RestClientException e) {
	                System.err.println("Error calling product service for Product ID " 
	                                   + orderDetail.getProductId() + ": " + e.getMessage());
	            }

	            // Check if a review exists for this product and add to status list
	            boolean reviewExists = reviewService.existsByCustomerAndProduct(buyerId, orderDetail.getProductId());
	            orderReviewStatuses.add(reviewExists);
	        }
	    }

	    // Add data to the model to be rendered in the view
	    model.addAttribute("orders", displayOrders);
	    model.addAttribute("reviews", orderReviewStatuses);

	    return "buyer-orders"; // Render the view showing buyer's orders
	}

	private Long getBuyerIdFromCookies(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("buyerId".equals(cookie.getName())) {
					return Long.parseLong(cookie.getValue());
				}
			}
		}
		return null;
	}

	@GetMapping("/receivedOrders")
	public String showReceivedOrders(Model model, HttpServletRequest request) {
	    Long sellerId = getSellerIdFromCookies1(request);

	    // Redirect to login if sellerId is null
	    if (sellerId == null) {
	        System.out.println("Seller ID is null. Redirecting to login page.");
	        return "redirect:http://localhost:9988/ecom/LoginPage";
	    }

	    System.out.println("Fetching orders for sellerId: " + sellerId);
	    List<Order_Detail> orders = orderdetailService.getOrdersBySellerId(sellerId);
	    List<ReceivedOrders> receivedOrdersList = new ArrayList<>();

	    for (Order_Detail order : orders) {
	        ReceivedOrders receivedOrder = new ReceivedOrders();
	        receivedOrder.setOrderId(order.getOrder().getOrderId());
	        receivedOrder.setOrderDate(order.getOrder().getOrderDate());
	        receivedOrder.setPrice(order.getPrice_per_unit());
	        receivedOrder.setQuantity(order.getQuantity());
	        receivedOrder.setStatus(order.getStatus());

	        // Get product details
	        String productUrl = "http://localhost:9988/products/cartController/" + order.getProductId();
	        try {
	            ResponseEntity<Product> productResponse = restTemplate.getForEntity(productUrl, Product.class);
	            Product product = productResponse.getBody();
	            if (product != null) {
	                receivedOrder.setImage(product.getImage());
	                receivedOrder.setProductName(product.getProductName());
	            }
	        } catch (Exception e) {
	            System.err.println("Error fetching product data: " + e.getMessage());
	        }

	        // Get buyer details
	        String buyerUrl = "http://localhost:9988/ecom/sellerController?buyerId=" + order.getOrder().getBuyerId();
	        try {
	            ResponseEntity<Buyer> buyerResponse = restTemplate.getForEntity(buyerUrl, Buyer.class);
	            Buyer buyer = buyerResponse.getBody();
	            if (buyer != null) {
	                receivedOrder.setName(buyer.getFirstName() + " " + buyer.getLastName());
	                receivedOrder.setAddress(
	                    buyer.getStreet() + "\n" + buyer.getCity() + "\n" + buyer.getState()
	                );
	            }
	        } catch (Exception e) {
	            System.err.println("Error fetching buyer data: " + e.getMessage());
	        }

	        receivedOrdersList.add(receivedOrder);
	    }

	    // Add the final list to the model
	    model.addAttribute("orders", receivedOrdersList);
	    System.out.println("Completed fetching received orders. Total orders: " + receivedOrdersList.size());

	    return "Seller_ReceivedOrder"; // Thymeleaf template
	}
	private Long getSellerIdFromCookies(HttpServletRequest request) {
	    Cookie[] cookies = request.getCookies();
	    if (cookies != null) {
	        for (Cookie cookie : cookies) {
	            if ("sellerId".equals(cookie.getName())) {
	                return Long.parseLong(cookie.getValue());
	            }
	        }
	    }
	    return null;
	}


	@GetMapping("/cart/buyNow")
	public String checkout(Model model, HttpServletRequest request) {
		Long buyerId = getBuyerIdFromCookies(request);
		List<Cart> cartItems = new ArrayList<>();
		List<Product> productItems = new ArrayList<>();
		double totalPrice = 0;
		if (buyerId != null) {
			String cartServiceUrl = "http://localhost:9988/cart/allproductController/" + buyerId;
			ResponseEntity<Cart[]> responseEntity = restTemplate.getForEntity(cartServiceUrl, Cart[].class);
			if (responseEntity.getStatusCode().is2xxSuccessful()) {
				cartItems = Arrays.asList(responseEntity.getBody());

				for (Cart cartItem : cartItems) {
					String cartServiceUrl3 = "http://localhost:9988/products/cartController/" + cartItem.getProductId();
					ResponseEntity<Product> responseEntity3 = restTemplate.getForEntity(cartServiceUrl3, Product.class);
					Product product = responseEntity3.getBody();
					product.setQuantity(cartItem.getQuantity());
					product.setPrice(cartItem.getPrice() - product.getDiscountPrice());
					double dummy = cartItem.getPrice() * cartItem.getQuantity();
					totalPrice += dummy - product.getDiscountPrice() * cartItem.getQuantity();
					productItems.add(product);
				}
			}
			String cartServiceUrl2 = "http://localhost:9988/ecom/sellerController" + "?buyerId=" + buyerId;
			ResponseEntity<Buyer> responseEntity2 = restTemplate.getForEntity(cartServiceUrl2, Buyer.class);
			Buyer buyer = responseEntity2.getBody();

			if (buyer == null) {
				throw new RuntimeException("Buyer not found");
			}
			model.addAttribute("cartItems", productItems);
			model.addAttribute("totalPrice", totalPrice);
			model.addAttribute("buyer", buyer); // Display buyer's shipping info
		}
		return "checkout"; // Render checkout view
	}
	
	
	@GetMapping("/reviewController")
	public ResponseEntity<ReviewProducts> showProduct(@RequestParam Long id) {
	    // Fetch reviews by product ID
	    List<Review> reviews = reviewService.getReviewsByProductId(id);
	    List<ReviewForBuyer> rev=new ArrayList<>();
	    // Default values
	    double averageRating = 0;
	    int reviewCount = 0;
	    int[] starCounts = new int[5]; // Index 0 for 1 star, index 1 for 2 stars, etc.

	    // Calculate average rating, review count, and star counts if reviews exist
	    if (!reviews.isEmpty()) {
	        averageRating = reviews.stream()
	                               .mapToInt(Review::getRating)
	                               .average()
	                               .orElse(0);
	        reviewCount = reviews.size();

	        // Count reviews per star rating
	        for (Review review : reviews) {
	        	ReviewForBuyer rfb=new ReviewForBuyer();
	            int starRating = review.getRating();
	            if (starRating >= 1 && starRating <= 5) {
	                starCounts[starRating - 1]++;
	            }
	            String cartServiceUrl2 = "http://localhost:9988/ecom/sellerController" + "?buyerId=" + review.getBuyerId();
				ResponseEntity<Buyer> responseEntity2 = restTemplate.getForEntity(cartServiceUrl2, Buyer.class);
				Buyer buyer = responseEntity2.getBody();

				if (buyer == null) {
					throw new RuntimeException("Buyer not found");
				}
				rfb.setComment(review.getComment());
				rfb.setRating(review.getRating());
				rfb.setName(buyer.getFirstName()+" "+buyer.getLastName());
				rev.add(rfb);
	        }
	    }

	    // Create the ReviewProducts object
	    ReviewProducts reviewProducts = new ReviewProducts(rev, averageRating, reviewCount, starCounts);

	    // Return the ReviewProducts object in the ResponseEntity
	    return ResponseEntity.ok(reviewProducts);
	}
	
	@PostMapping("/updateOrderStatus")
	public String updateOrderStatus(@RequestParam("orderId") Long orderId, 
	                                 @RequestParam("status") String status,
	                                 Model model, 
	                                 HttpServletRequest request) {
	    try {
	        orderdetailService.updateOrderStatus(orderId, status);
	        return "redirect:/order/CompletedOrders"; // Make sure this URL matches your mapping
	    } catch (Exception e) {
	        model.addAttribute("errorMessage", "Failed to update order status.");
	        return "Seller_ReceivedOrder"; // or another relevant view
	    }
	}
	

}
