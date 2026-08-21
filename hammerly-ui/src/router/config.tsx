import type { RouteObject } from "react-router-dom";
import Home from "../pages/home/page";
import AuctionDetail from "../pages/auction-detail/page";
import Auctions from "../pages/auctions/page";
import Guide from "../pages/guide/page";
import Auth from "../pages/auth/page";
import Profile from "../pages/profile/page";
import Cart from "../pages/cart/page";

const routes: RouteObject[] = [
  {
    path: "/",
    element: <Home />,
  },
  {
    path: "/auctions",
    element: <Auctions />,
  },
  {
    path: "/auction/:id",
    element: <AuctionDetail />,
  },
  {
    path: "/guide",
    element: <Guide />,
  },
  {
    path: "/auth",
    element: <Auth />,
  },
    {
    path: "/profile",
    element: <Profile />,
  },
  {
    path: "/cart",
    element: <Cart />,
  },
];

export default routes;
