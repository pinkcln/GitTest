package com.pink.mall.controller.force;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.pink.mall.controller.BaseController;
import com.pink.mall.entity.*;
import com.pink.mall.service.*;
import com.pink.mall.util.OrderUtil;
import com.pink.mall.util.PageUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class ForeOrderController extends BaseController {

    @Resource
    private ProductOrderItemService productOrderItemService;
    @Resource
    private ProductService productService;
    @Resource
    private UserService userService;
    @Resource
    private ProductImageService productImageService;
    @Resource
    private CategoryService categoryService;
    @Resource
    private AddressService addressService;
    @Resource
    private ProductOrderService productOrderService;
    @Resource
    private LastIDService lastIDService;
    @Resource
    private ReviewService reviewService;


    //创建订单项-购物车-ajax
    @ResponseBody
    @RequestMapping(value = "orderItem/create/{productId}",method = RequestMethod.POST,produces = "application/json;charset=utf-8")
    public String createOrderItem(@PathVariable("productId")Integer productId,
                                  @RequestParam(required = false,defaultValue = "1") Short productNumber,
                                  HttpSession session,
                                  HttpServletRequest request){
        JSONObject object=new JSONObject();
        logger.info("检查用户是否登录");
        Object userId=checkUser(session);
        if(userId == null){
            object.put("url","/login");
            object.put("success",false);
            return object.toJSONString();
        }

        logger.info("通过产品id获取产品信息：{}",productId);
        Product product=productService.get(productId);
        if(product == null){
            object.put("url","/login");
            object.put("success",false);
            return object.toJSONString();
        }
        ProductOrderItem productOrderItem=new ProductOrderItem();
        logger.info("检查用户的购物车项");

        List<ProductOrderItem> orderItemList=productOrderItemService.getListByUserId(Integer.valueOf(userId.toString()),null);
        for (ProductOrderItem orderItem:orderItemList){
            if (orderItem.getProductOrderItemProduct().getProductId().equals(productId)){
                logger.info("找到已有的商品，进行数量追加");
                int number = orderItem.getProductOrderItemNumber();
                number+=1;
                //获取购物车中原商品的购物id，进行修改
                productOrderItem.setProductOrderItemId(orderItem.getProductOrderItemId());
                //把新的购物车商品数量修改到指定记录
                productOrderItem.setProductOrderItemNumber((short)number);
                //因为数量发生改变，所以总价也需要重新计算，并且修改到原纪录中
                productOrderItem.setProductOrderItemPrice(number * product.getProductSalePrice());
                //开始修改购物车
                boolean yn=productOrderItemService.update(productOrderItem);
                if(yn){
                    object.put("success",true);
                }else{
                    object.put("success",false);
                }
                return object.toJSONString();
            }
        }

        logger.info("封装订单项对象");
        productOrderItem.setProductOrderItemProduct(product);
        productOrderItem.setProductOrderItemNumber(productNumber);
        productOrderItem.setProductOrderItemPrice(product.getProductSalePrice() * productNumber);
        productOrderItem.setProductOrderItemUser(new User(Integer.valueOf(userId.toString())));
        boolean yn = productOrderItemService.add(productOrderItem);
        if(yn){
            object.put("success",true);
        }else{
            object.put("success",false);
        }
        return object.toJSONString();
    }




    //转到前台Mall-购物车页
    @RequestMapping(value = "cart",method = RequestMethod.GET)
    public String goToCartPage(Map<String,Object> map,HttpSession session){
        logger.info("检查用户会否登录");
        Object userId=checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else {
            return "redirect:/login";
        }
        logger.info("获取用户购物车信息");
        List<ProductOrderItem> orderItemList=productOrderItemService.getListByUserId(Integer.valueOf(userId.toString()),null);
        Integer orderItemTotal=0;
        if(orderItemList.size()>0){
            logger.info("获取用户购物车的商品总数");
            orderItemTotal = productOrderItemService.getTotalByUserId(Integer.parseInt(userId.toString()));
            logger.info("获取用户购物车内的商品信息");
            for(ProductOrderItem orderItem : orderItemList){
                //获取购物车中的每一个商品的id
                Integer productId = orderItem.getProductOrderItemProduct().getProductId();
                //通过商品id去查询商品的信息
                Product product = productService.get(productId);
                product.setSingleProductImageList(productImageService.getList(productId,(byte)0,null));
                product.setProductCategory(categoryService.get(product.getProductCategory().getCategoryId()));
                orderItem.setProductOrderItemProduct(product);
            }
        }
        map.put("orderItemList",orderItemList);
        map.put("orderItemTotal",orderItemTotal);

        logger.info("转到前台Mall-购物车页");
        return "fore/productBuyCarPage";
    }



    //删除订单项-购物车-ajax
    @ResponseBody
    @RequestMapping(value = "orderItem/{orderItem_id}",method = RequestMethod.DELETE,produces = "application/json;charset=utf-8")
    public String deleteOrderItem(@PathVariable("orderItem_id") Integer orderItem_id,
                                  HttpSession session,
                                  HttpServletRequest request){
        JSONObject object=new JSONObject();
        logger.info("检查用户是否登录");
        Object userId=checkUser(session);
        if(userId ==  null){
            object.put("url","/login");
            object.put("success",false);
            return object.toJSONString();
        }
        logger.info("检查用户购物车项");
        List<ProductOrderItem> orderItemList=productOrderItemService.getListByUserId(Integer.valueOf(userId.toString()),null);
        boolean isMine = false;
        for (ProductOrderItem orderItem : orderItemList){
            logger.info("找到匹配的购物车项");
            if(orderItem.getProductOrderItemId().equals(orderItem_id)) {
                isMine = true;
                break;
            }
        }
        if(isMine){
            logger.info("删除订单项信息");
            boolean yn = productOrderItemService.deleteList(new Integer[]{orderItem_id});
            if(yn){
                object.put("success",true);
            }else {
                object.put("success",false);
            }
        }else{
            object.put("success",false);
        }
        return object.toJSONString();
    }


    //更新购物车订单项数量-ajax
    @ResponseBody
    @RequestMapping(value = "orderItem",method = RequestMethod.PUT,produces = "application/json;charset=utf-8")
    public String updateOrderItem(HttpSession session,Map<String,Object> map,HttpServletResponse response,
                                  @RequestParam String orderItemMap){
        JSONObject object = new JSONObject();
        logger.info("检查用户是否登录");
        Object userId=checkUser(session);
        if(userId == null){
            object.put("success",false);
            return object.toJSONString();
        }
        JSONObject orderItemString = JSON.parseObject(orderItemMap);
        Set<String> orderItemIDSet=orderItemString.keySet();
        if(orderItemIDSet.size()>0){
            logger.info("更新产品订单项数量");
            for (String key : orderItemIDSet){
                //通过购物车id获取购物车中的商品信息
                ProductOrderItem productOrderItem=productOrderItemService.get(Integer.parseInt(key));
                if(productOrderItem == null || !productOrderItem.getProductOrderItemUser().getUserId().equals(userId)){
                    logger.warn("订单项为空或用户状态不一致！");
                    object.put("success",false);
                    return object.toJSONString();
                }
                if (productOrderItem.getProductOrderItemOrder() != null){
                    logger.warn("用户订单项不属于购物车，回到购物车页");
                    return "redirect:/cart";
                }
                Short number = Short.valueOf(orderItemString.getString(key.toString()));
                if (number <= 0 || number > 500){
                    logger.warn("订单项产品数量不合法！");
                    object.put("success",false);
                    return object.toJSONString();
                }
                //计算购物车中商品的单价
                double price=productOrderItem.
                        getProductOrderItemPrice() / productOrderItem.getProductOrderItemNumber();
                ProductOrderItem productOrderItem1 = new ProductOrderItem();
                productOrderItem1.setProductOrderItemId(Integer.valueOf(key));
                productOrderItem1.setProductOrderItemNumber(number);
                productOrderItem1.setProductOrderItemPrice(number * price);
                Boolean yn = productOrderItemService.update(productOrderItem1);
                if(!yn){
                    throw new RuntimeException();
                }
            }
            Object[] orderItemIDArray = orderItemIDSet.toArray();
            object.put("success",true);
            object.put("orderItemIDArray",orderItemIDArray);
            return object.toJSONString();
        }else{
            logger.warn("无订单项可以处理");
            object.put("success",false);
            return object.toJSONString();
        }

    }


    //转到前台Mall-购物车订单建立页
    @RequestMapping(value = "order/create/byCart",method = RequestMethod.GET)
    public String goToOrderConfirmPageByCart(Map<String,Object> map,
                                             HttpSession session,HttpServletRequest request,
                                             @RequestParam(required = false)Integer[] order_item_list)throws Exception{

        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else{
            return "redirect:/login";
        }
        if (order_item_list == null || order_item_list.length == 0){
            logger.warn("用户订单项数组不存在，回到购物车页");
            return "redirect:/cart";
        }
        logger.info("通过订单项ID数组获取订单信息");
        List<ProductOrderItem> orderItemList = new ArrayList<>(order_item_list.length);
        for(Integer orderItem_id : order_item_list){
            orderItemList.add(productOrderItemService.get(orderItem_id));
        }
        logger.info("------检查订单项合法性-----");
        if(orderItemList.size() == 0){
            logger.warn("用户订单项获取失败，回到购物车");
            return "redirect:/cart";
        }
        for (ProductOrderItem orderItem : orderItemList){
            if(orderItem.getProductOrderItemUser().getUserId() != userId){
                logger.warn("用户订单项与用于不匹配，回到购物车页");
                return "redirect:/cart";
            }
            if(orderItem.getProductOrderItemOrder() != null){
                logger.warn("用户订单项不属于购物车，回到购物车页");
                return "redirect:/cart";
            }
        }
        logger.info("验证通过，获取订单项的产品信息");
        double orderTotalPrice = 0.0;
        for (ProductOrderItem orderItem : orderItemList){
            Product product = productService.get(orderItem.getProductOrderItemProduct().getProductId());
            product.setProductCategory(categoryService.get(product.getProductCategory().getCategoryId()));
            product.setSingleProductImageList(productImageService.getList(product.getProductId(),(byte)0,new PageUtil(0,1)));
            orderItem.setProductOrderItemProduct(product);
            orderTotalPrice += orderItem.getProductOrderItemPrice();
        }
        String addressId = "110000";
        String cityAddressId = "110100";
        String districtAddressId = "110101";
        String detailsAddress = null;   //收货的详细地址
        String order_post = null;       //邮政编码
        String order_receiver = null;   //收货人
        String order_phone = null;      //联系电话
        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for (Cookie cookie : cookies){
                String cookieName = cookie.getName();
                String cookieValue = cookie.getValue();
                switch (cookieName){
                    case "addressId":
                        addressId = cookieValue;
                        break;
                    case "cityAddressId":
                        cityAddressId = cookieValue;
                        break;
                    case "districtAddressId":
                        districtAddressId = cookieValue;
                        break;
                    case "order_post":
                        order_post = URLDecoder.decode(cookieValue,"UTF-8");
                        break;
                    case "order_receiver":
                        order_receiver = URLDecoder.decode(cookieValue,"UTF-8");
                        break;
                    case "order_phone":
                        order_phone = URLDecoder.decode(cookieValue,"UTF-8");
                        break;
                    case "detailsAddress":
                        detailsAddress = URLDecoder.decode(cookieValue,"UTF-8");
                        break;
                }
            }
        }
        logger.info("获取省份信息");
        List<Address> addressList = addressService.getRoot();
        logger.info("获取addressId为{}的市级地址信息",addressId);
        List<Address> cityAddress = addressService.getList(null,addressId);
        logger.info("获取cityAddressId为{}的市级地址信息",addressId);
        List<Address> districtAddress = addressService.getList(null,cityAddressId);
        map.put("orderItemList",orderItemList);
        map.put("addressList",addressList);
        map.put("cityList",cityAddress);
        map.put("districtList",districtAddress);
        map.put("orderTotalPrice",orderTotalPrice);
        map.put("addressId",addressId);
        map.put("cityAddressId",cityAddressId);
        map.put("districtAddressId",districtAddressId);
        map.put("order_post",order_post);
        map.put("order_receiver",order_receiver);
        map.put("order_phone",order_phone);
        map.put("detailsAddress",detailsAddress);
        logger.info("转到前台Mall-订单建立页");
        return "fore/productBuyPage";
    }


    @ResponseBody
    @RequestMapping(value = "order/list",method = RequestMethod.POST,produces = "application/json;charset=utf-8")
    public String createOrderByList(HttpSession session,Map<String,Object> map,HttpServletResponse response,
                                    @RequestParam String addressId,
                                    @RequestParam String cityAddressId,
                                    @RequestParam String districtAddressId,
                                    @RequestParam String productOrderDetailAddress,
                                    @RequestParam String productOrderPost,
                                    @RequestParam String productOrderReceiver,
                                    @RequestParam String productOrderMobile,
                                    @RequestParam String orderItemJSON) throws UnsupportedEncodingException{
        JSONObject object = new JSONObject();
        logger.info("检查用户是否登录");
        Object userId=checkUser(session);
        if(userId == null){
            object.put("success",false);
            object.put("url","/login");
            return object.toJSONString();
        }
        JSONObject orderItemMap = JSONObject.parseObject(orderItemJSON);
        Set<String> orderItem_id = orderItemMap.keySet();
        List<ProductOrderItem> productOrderItemList = new ArrayList<>(3);
        if(orderItem_id.size() > 0){
            for (String id : orderItem_id){
                ProductOrderItem orderItem = productOrderItemService.get(Integer.valueOf(id));
                if (orderItem == null || !orderItem.getProductOrderItemUser().getUserId().equals(userId)){
                    logger.warn("订单项为空或用户状态不一致");
                    object.put("success",false);
                    object.put("url","/cart");
                    return object.toJSONString();
                }
                if(orderItem.getProductOrderItemOrder() != null){
                    logger.warn("用户订单项不属于购物车，回到购物车页");
                    object.put("success",false);
                    object.put("url","/cart");
                    return object.toJSONString();
                }
                ProductOrderItem productOrderItem = new ProductOrderItem();
                productOrderItem.setProductOrderItemId(Integer.valueOf(id));
                productOrderItem.setProductOrderItemUserMessage(orderItemMap.getString(id));
                boolean yn = productOrderItemService.update(productOrderItem);
                if(!yn){
                    throw new RuntimeException();
                }
                orderItem.setProductOrderItemProduct(productService.get(orderItem.getProductOrderItemProduct().getProductId()));
                productOrderItemList.add(orderItem);
            }
        }else {
            object.put("success",false);
            object.put("url","/cart");
            return object.toJSONString();
        }
        logger.info("将收货地址等相关信息存入Cookie");
        Cookie cookie1 = new Cookie("addressId",addressId);
        Cookie cookie2 = new Cookie("cityAddressId",cityAddressId);
        Cookie cookie3 = new Cookie("districtAddressId",districtAddressId);
        Cookie cookie4 = new Cookie("order_post", URLEncoder.encode(productOrderPost,"UTF-8"));
        Cookie cookie5 = new Cookie("order_receiver", URLEncoder.encode(productOrderReceiver,"UTF-8"));
        Cookie cookie6 = new Cookie("order_phone", URLEncoder.encode(productOrderMobile,"UTF-8"));
        Cookie cookie7 = new Cookie("detailsAddress", URLEncoder.encode(productOrderDetailAddress,"UTF-8"));
        int maxAge = 1 * 60 * 60 * 24 * 365; //设置过期时间为一年
        cookie1.setMaxAge(maxAge);
        cookie2.setMaxAge(maxAge);
        cookie3.setMaxAge(maxAge);
        cookie4.setMaxAge(maxAge);
        cookie5.setMaxAge(maxAge);
        cookie6.setMaxAge(maxAge);
        cookie7.setMaxAge(maxAge);
        response.addCookie(cookie1);
        response.addCookie(cookie2);
        response.addCookie(cookie3);
        response.addCookie(cookie4);
        response.addCookie(cookie5);
        response.addCookie(cookie6);
        response.addCookie(cookie7);
        StringBuffer productOrderCode = new StringBuffer()
                .append(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()))
                .append(0)
                .append(userId);
        logger.info("生成的订单号为：{}",productOrderCode);
        logger.info("整合订单对象");
        ProductOrder productOrder = new ProductOrder();
        productOrder.setProductOrderStatus((byte)0);
        productOrder.setProductOrderAddress(new Address(districtAddressId));
        productOrder.setProductOrderPost(productOrderPost);
        productOrder.setProductOrderUser(new User(Integer.valueOf(userId.toString())));
        productOrder.setProductOrderMobile(productOrderMobile);
        productOrder.setProductOrderReceiver(productOrderReceiver);
        productOrder.setProductOrderDetailAddress(productOrderDetailAddress);
        productOrder.setProductOrderPayDate(new Date());
        productOrder.setProductOrderCode(productOrderCode.toString());
        Boolean yn = productOrderService.add(productOrder);
        if( !yn ){
            throw new RuntimeException();
        }
        Integer orderId = lastIDService.selectLastID();
        logger.info("整合订单项对象");
        for (ProductOrderItem orderItem : productOrderItemList){
            orderItem.setProductOrderItemOrder(new ProductOrder(orderId));
            yn = productOrderItemService.update(orderItem);
        }
        if(!yn){
            throw new RuntimeException();
        }

        object.put("success",true);
        object.put("url","/order/pay/"+productOrder.getProductOrderCode());
        return object.toJSONString();
    }


    //转到前台Mall-订单支付页
    @RequestMapping(value = "order/pay/{order_code}",method = RequestMethod.GET)
    public String goToOrderPayPage(Map<String,Object> map,HttpSession session,
                                   @PathVariable("order_code") String order_code){
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else {
            return "redirect:/login";
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 0){
            logger.warn("订单状态不正确，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证用户与订单信息是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单信息不一致，返回订单列表页");
            return "redirect:/order/0/10";
        }

        order.setProductOrderItemList(productOrderItemService.getListByOrderId(order.getProductOrderId(),null));

        double orderTotalPrice = 0.00;
        if(order.getProductOrderItemList().size() == 1){
            logger.info("获取订单项的产品信息");
            ProductOrderItem productOrderItem = order.getProductOrderItemList().get(0);
            Product product = productService.get(productOrderItem.getProductOrderItemProduct().getProductId());
            product.setProductCategory(categoryService.get(product.getProductCategory().getCategoryId()));
            productOrderItem.setProductOrderItemProduct(product);
            orderTotalPrice = productOrderItem.getProductOrderItemPrice();
        }else {
            for (ProductOrderItem productOrderItem : order.getProductOrderItemList()){
                orderTotalPrice += productOrderItem.getProductOrderItemPrice();
            }
        }
        logger.info("订单总金额为：{}元",orderTotalPrice);

        map.put("productOrder",order);
        map.put("orderTotalPrice",orderTotalPrice);

        logger.info("转到前台Mall-订单支付页");
        return "fore/productPayPage";
    }


    //更新订单信息为已支付，待发货-ajax
    @ResponseBody
    @RequestMapping(value = "order/pay/{order_code}",method = RequestMethod.PUT)
    public String orderPay(HttpSession session,@PathVariable("order_code")String order_code){
        JSONObject object = new JSONObject();
        logger.info("检查用户是否登录");
        Object userId=checkUser(session);
        if(userId == null){
            object.put("success",false);
            object.put("url","/login");
            return object.toJSONString();
        }
        logger.info("------验证名单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 0){
            logger.warn("订单状态不正确，返回订单列表页");
            object.put("success",false);
            object.put("url","order/0/10");
            return object.toJSONString();
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单信息不一致，返回订单页");
            object.put("success",false);
            object.put("url","order/0/10");
            return object.toJSONString();
        }
        order.setProductOrderItemList(productOrderItemService.getListByOrderId(order.getProductOrderId(),null));

        double orderTotalPrice = 0.00;
        if(order.getProductOrderItemList().size() == 1){
            logger.info("获取订单项的产品信息");
            ProductOrderItem productOrderItem = order.getProductOrderItemList().get(0);
            Product product = productService.get(productOrderItem.getProductOrderItemProduct().getProductId());
            product.setProductCategory(categoryService.get(product.getProductCategory().getCategoryId()));
            productOrderItem.setProductOrderItemProduct(product);
            orderTotalPrice = productOrderItem.getProductOrderItemPrice();
        }else{
            for (ProductOrderItem productOrderItem : order.getProductOrderItemList()){
                orderTotalPrice += productOrderItem.getProductOrderItemPrice();
            }
        }
        logger.info("总共支付金额为：{}元",orderTotalPrice);
        logger.info("更新订单信息");
        ProductOrder productOrder = new ProductOrder();
        productOrder.setProductOrderId(order.getProductOrderId());
        productOrder.setProductOrderPayDate(new Date());
        productOrder.setProductOrderStatus((byte)1);

        boolean yn = productOrderService.update(productOrder);
        if(yn){
            object.put("success",true);
            object.put("url","/order/pay/success/"+order_code);
        }else {
            object.put("success",false);
            object.put("url","order/0/10");
        }
        return object.toJSONString();
    }


    //转到前台Mall-订单支付成功页
    @RequestMapping(value = "order/pay/success/{order_code}",method = RequestMethod.GET)
    public String goToOrderPaySuccessPage(Map<String,Object> map,HttpSession session,
                                          @PathVariable("order_code")String order_code){
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else {
            return "redirect:/login";
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 1){
            logger.warn("订单状态不正确，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单不一致，返回订单列表页");
            return "redirect:/order/0/10";
        }
        order.setProductOrderItemList(productOrderItemService.getListByOrderId(order.getProductOrderId(),null));

        double orderTotalPrice = 0.00;
        if(order.getProductOrderItemList().size() == 1){
            logger.info("获取订单项的信息");
            ProductOrderItem productOrderItem = order.getProductOrderItemList().get(0);
            orderTotalPrice = productOrderItem.getProductOrderItemPrice();
        }else {
            for (ProductOrderItem productOrderItem : order.getProductOrderItemList()){
                orderTotalPrice += productOrderItem.getProductOrderItemPrice();
            }
        }
        logger.info("订单总金额为：{}元",orderTotalPrice);

        logger.info("获取订单详情-地址信息");
        Address address = addressService.get(order.getProductOrderAddress().getAddressAreaId());
        Stack<String> addressStack = new Stack<>();
        //详细地址
        addressStack.push(order.getProductOrderDetailAddress());
        //最后一级地址
        addressStack.push(address.getAddressName() + " ");
        //如果不是第一级地址
        while (!address.getAddressAreaId().equals(address.getAddressRegionId().getAddressAreaId())){
            address = addressService.get(address.getAddressRegionId().getAddressAreaId());
            addressStack.push(address.getAddressName() + " ");
        }
        StringBuilder builder = new StringBuilder();
        while (!addressStack.empty()){
            builder.append(addressStack.pop());
        }
        logger.info("订单地址字符串：{}",builder);
        order.setProductOrderDetailAddress(builder.toString());
        map.put("productOrder",order);
        map.put("orderTotalPrice",orderTotalPrice);
        logger.info("转到前台Mall-订单支付成功页");
        return "fore/productPaySuccessPage";
    }


    //转到前台Mall-订单列表页
    @RequestMapping(value = "order",method = RequestMethod.GET)
    public String goToPageSimple(){
        return "redirect:/order/0/10";
    }

    @RequestMapping(value = "order/{index}/{count}",method = RequestMethod.GET)
    public String gotoPage(HttpSession session,Map<String,Object> map,
                           @RequestParam(required = false)Byte status,
                           @PathVariable("index") Integer index /*页数*/,
                           @PathVariable("count") Integer count /*行数*/){
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else{
            return "redirect:/login";
        }
        Byte[] status_array = null;
        if(status != null){
            status_array = new Byte[]{status};
        }

        PageUtil pageUtil=new PageUtil(index,count);
        logger.info("根据用户ID：{}获取订单列表",userId);
        ProductOrder productOrder = new ProductOrder();
        productOrder.setProductOrderUser(new User(Integer.valueOf(userId.toString())));
        List<ProductOrder> productOrderList = productOrderService.getList(productOrder,
                status_array,new OrderUtil("productOrderId",true),pageUtil);

        //订单总数量
        Integer orderCount = 0;
        if(productOrderList.size() > 0){
            orderCount = productOrderService.getTotal(productOrder,
                    status_array);
            for (ProductOrder order : productOrderList){
                //通过订单id查询订单详细列表
                List<ProductOrderItem> productOrderItemList = productOrderItemService.getListByOrderId(order.getProductOrderId(),null);
                if(productOrderItemList != null){
                    for (ProductOrderItem productOrderItem : productOrderItemList){
                        //获取商品id
                        Integer productId = productOrderItem.getProductOrderItemProduct().getProductId();
                        //根据商品id查询商品详情
                        Product product = productService.get(productId);
                        //根据id查询当前商品的预览图片
                        product.setSingleProductImageList(productImageService.getList(productId,(byte) 0,new PageUtil(0,1)));
                        //芭莎股票设置到订单详情对象中
                        productOrderItem.setProductOrderItemProduct(product);
                        //判断订单状态是否是确认收货
                        if(order.getProductOrderStatus() == 3){
                            //根据订单详情id查询评论表，大于0则表示已经评论过
                            productOrderItem.setIsReview(reviewService.getTotalByOrderItemId(productOrderItem.
                                    getProductOrderItemId())>0);
                        }
                    }
                }
                order.setProductOrderItemList(productOrderItemList);
            }
        }
        pageUtil.setTotal(orderCount);

        logger.info("获取产品分类列表信息");
        List<Category> categoryList = categoryService.getList(null,new PageUtil(0,5));

        map.put("pageUtil",pageUtil);
        map.put("productOrderList",productOrderList);
        map.put("categoryList",categoryList);
        map.put("status",status);

        logger.info("转到前台Mall-订单列表页");
        return "fore/orderListPage";
    }


    //更新订单信息为已发货，待确认-ajax
    @RequestMapping(value = "order/delivery/{order_code}",method = RequestMethod.GET)
    public String orderDelivery(HttpSession session,@PathVariable("order_code")String order_code){
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        if(userId == null){
            return "redirect:/login";
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 1){
            logger.warn("订单状态不正确，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单不一致，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("更新订单信息");
        ProductOrder productOrder = new ProductOrder();
        productOrder.setProductOrderId(order.getProductOrderId());
        productOrder.setProductOrderDeliveryDate(new Date());
        productOrder.setProductOrderStatus((byte)2);

        productOrderService.update(productOrder);

        return "redirect:/order/0/10";
    }


    //更新订单信息为交易关闭-ajax
    @ResponseBody
    @RequestMapping(value = "order/close/{order_code}",method = RequestMethod.PUT,produces = "application/json;charset=utf-8")
    public String orderClose(HttpSession session,@PathVariable("order_code")String order_code){
        JSONObject object = new JSONObject();
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        if(userId == null){
            object.put("success",false);
            object.put("url","/login");
            return object.toJSONString();
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 0){
            logger.warn("订单状态不正确，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单不一致，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("更新订单信息");
        ProductOrder productOrder = new ProductOrder();
        productOrder.setProductOrderStatus((byte)4);
        productOrder.setProductOrderId(order.getProductOrderId());

        boolean yn = productOrderService.update(productOrder);
        if(yn){
            object.put("success",true);
        }else {
            object.put("success",false);
        }
        return object.toJSONString();
    }


    //转到前台Mall-订单确认页
    @RequestMapping(value = "order/confirm/{order_code}",method = RequestMethod.GET)
    public String goToOrderConfirmPage(Map<String,Object> map,HttpSession session,
                                       @PathVariable("order_code")String order_code){
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else{
            return "redirect:/login";
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 2){
            logger.warn("订单状态不正确，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单不一致，返回订单列表页");
            return "redirect:/order/0/10";
        }
        order.setProductOrderItemList(productOrderItemService.getListByOrderId(order.getProductOrderId(),null));

        double orderTotalPrice = 0.00;
        if(order.getProductOrderItemList().size() == 1){
            logger.info("获取订单项的产品信息");
            ProductOrderItem productOrderItem = order.getProductOrderItemList().get(0);
            Integer productId = productOrderItem.getProductOrderItemProduct().getProductId();
            Product product = productService.get(productId);
            product.setSingleProductImageList(productImageService.getList(productId,(byte)0,new PageUtil(0,1)));
            productOrderItem.setProductOrderItemProduct(product);
            orderTotalPrice = productOrderItem.getProductOrderItemPrice();
        }else {
            logger.info("获取多订单项的产品信息");
            for (ProductOrderItem productOrderItem : order.getProductOrderItemList()){
                Integer productId = productOrderItem.getProductOrderItemProduct().getProductId();
                Product product = productService.get(productId);
                product.setSingleProductImageList(productImageService.getList(productId,(byte)0,new PageUtil(0,1)));
                productOrderItem.setProductOrderItemProduct(product);
                orderTotalPrice += productOrderItem.getProductOrderItemPrice();
            }
        }
        logger.info("订单总金额为：{}元",orderTotalPrice);
        map.put("productOrder",order);
        map.put("orderTotalPrice",orderTotalPrice);
        logger.info("转到前台Mall-订单确认页");
        return "fore/orderConfirmPage";
    }


    //更新订单信息为交易成功-ajax
    @ResponseBody
    @RequestMapping(value = "order/success/{order_code}",method = RequestMethod.PUT,produces = "application/json;charset=utf-8")
    public String orderSuccess(HttpSession session,@PathVariable("order_code")String order_code){
        JSONObject object = new JSONObject();
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        if(userId == null){
            object.put("success",false);
            object.put("url","/login");
            return object.toJSONString();
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 2){
            logger.warn("订单状态不正确，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单不一致，返回订单列表页");
            object.put("success",false);
            object.put("url","/order/0/10");
            return object.toJSONString();
        }
        logger.info("更新订单信息");
        ProductOrder productOrder = new ProductOrder();
        productOrder.setProductOrderId(order.getProductOrderId());
        productOrder.setProductOrderStatus((byte)3);
        productOrder.setProductOrderConfirmDate(new Date());

        boolean yn = productOrderService.update(productOrder);
        if(yn){
            object.put("success",true);
        }else {
            object.put("success",false);
        }
        return object.toJSONString();
    }


    //转到前台Mall-订单完成页
    @RequestMapping(value = "order/success/{order_code}",method = RequestMethod.GET)
    public String goToOrderSuccessPage(Map<String,Object> map,HttpSession session,
                                       @PathVariable("order_code")String order_code){
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);
        User user;
        if(userId != null){
            logger.info("获取用户信息");
            user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user",user);
        }else{
            return "redirect:/login";
        }
        logger.info("------验证订单信息------");
        logger.info("查询订单是否存在");
        ProductOrder order = productOrderService.getByCode(order_code);
        if(order == null){
            logger.warn("订单不存在，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证订单状态");
        if(order.getProductOrderStatus() != 3){
            logger.warn("订单状态不正确，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("验证用户与订单是否一致");
        if(order.getProductOrderUser().getUserId() != Integer.parseInt(userId.toString())){
            logger.warn("用户与订单不一致，返回订单列表页");
            return "redirect:/order/0/10";
        }
        logger.info("获取订单中订单项的数量");
        Integer count = productOrderItemService.getTotalByOrderId(order.getProductOrderId());
        Product product = null;
        if(count == 1){
            logger.info("获取订单中的唯一订单项");
            ProductOrderItem productOrderItem = productOrderItemService.
                    getListByOrderId(order.getProductOrderId(),new PageUtil(0,1)).get(0);
            if (productOrderItem != null){
                logger.info("获取订单项评论数量");
                count = reviewService.getTotalByOrderItemId(productOrderItem.getProductOrderItemId());
                if (count == 0){
                    logger.info("获取订单项产品信息");
                    product = productService.get(productOrderItem.getProductOrderItemProduct().getProductId());
                    if(product != null){
                        product.setSingleProductImageList(productImageService.getList(product.getProductId(),(byte)0,new PageUtil(0,1)));
                    }
                }
            }
            map.put("orderItem",productOrderItem);
        }
        map.put("product",product);

        logger.info("转到前台Mall-订单完成页");
        return "fore/orderSuccessPage";
    }


}
