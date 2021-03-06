class Api::PostsController < Api::ApiController
  load_and_authorize_resource :post

  include NotifyOfMentions

  def create
    @post.save!
    @post.thread.mark_as_visited_for(current_user)
    PubSub.publish :created, :post, @post

    notify_mentioned_users!(@post)
  end

  def update
    @post.update!(update_params)
    PubSub.publish :updated, :post, @post
  end

private
  def create_params
    thread = DiscussionThread.find(params[:thread_id])
    post_params.merge(thread: thread, author: current_user)
  end

  def update_params
    post_params
  end

  def post_params
    params.require(:post).permit(:body)
  end
end
